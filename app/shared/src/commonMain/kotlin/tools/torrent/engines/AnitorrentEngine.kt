package me.him188.ani.app.tools.torrent.engines

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.app.data.models.preference.MediaSourceProxySettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.source.media.fetch.toClientProxyConfig
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.app.platform.versionCode
import me.him188.ani.app.tools.torrent.AbstractTorrentEngine
import me.him188.ani.app.tools.torrent.TorrentEngineConfig
import me.him188.ani.app.tools.torrent.TorrentEngineType
import me.him188.ani.app.torrent.anitorrent.AnitorrentDownloaderFactory
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.api.TorrentDownloaderFactory
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext

@Serializable
data class AnitorrentConfig(
    /**
     * 设置为 [FileSize.Unspecified] 表示无限
     */
    val downloadRateLimit: FileSize = FileSize.Unspecified,
    /**
     * 设置为 [FileSize.Unspecified] 表示无限, [FileSize.Zero] 表示不允许上传
     */
    val uploadRateLimit: FileSize = DEFAULT_UPLOAD_RATE_LIMIT,
    @Transient private val _placeholder: Int = 0,
) : TorrentEngineConfig {
    companion object {
        val DEFAULT_UPLOAD_RATE_LIMIT = 2.megaBytes

        val Default = AnitorrentConfig()
    }
}

class AnitorrentEngine(
    config: Flow<AnitorrentConfig>,
    proxySettings: Flow<ProxySettings>,
    private val saveDir: SystemPath,
    parentCoroutineContext: CoroutineContext,
    private val anitorrentFactory: TorrentDownloaderFactory = AnitorrentDownloaderFactory()
) : AbstractTorrentEngine<TorrentDownloader, AnitorrentConfig>(
    type = TorrentEngineType.Anitorrent,
    config = config,
    parentCoroutineContext = parentCoroutineContext,
    proxySettings = proxySettings.map { it.default },
) {
    override val location: MediaSourceLocation get() = MediaSourceLocation.Local
    override val isSupported: Flow<Boolean>
        get() = flowOf(tryLoadLibraries())

    private fun tryLoadLibraries(): Boolean {
        try {
            anitorrentFactory.libraryLoader.loadLibraries()
            logger.info { "Loaded libraries for AnitorrentEngine" }
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to load libraries for AnitorrentEngine" }
            return false
        }
    }

    override suspend fun testConnection(): Boolean = isSupported.first()

    // client 需要监听 proxySettings 更新, 否则在切换设置后必须重启才能生效
    private val client = proxySettings.map { proxySettings ->
        createDefaultHttpClient {
            install(UserAgent) {
                agent = getAniUserAgent()
            }
            proxy(proxySettings.default.toClientProxyConfig())
            expectSuccess = true
        }
    }.onReplacement {
        it.close()
    }.shareIn(scope, started = SharingStarted.Lazily, replay = 1)

    override suspend fun newInstance(
        config: AnitorrentConfig,
        proxySettings: MediaSourceProxySettings
    ): TorrentDownloader {
        if (!isSupported.first()) {
            logger.error { "Anitorrent is disabled because it is not built. Read `/torrent/anitorrent/README.md` for more information." }
            throw UnsupportedOperationException("AnitorrentEngine is not supported")
        }
        return anitorrentFactory.createDownloader(
            rootDataDirectory = saveDir,
            client.asHttpFileDownloader(),
            TorrentDownloaderConfig(
                peerFingerprint = computeTorrentFingerprint(),
                userAgent = computeTorrentUserAgent(),
                downloadRateLimitBytes = config.downloadRateLimit.toLibtorrentRate(),
                uploadRateLimitBytes = config.uploadRateLimit.toLibtorrentRate(),
            ),
            parentCoroutineContext = scope.coroutineContext,
        )
    }
}

private fun FileSize.toLibtorrentRate(): Int = when (this) {
    FileSize.Unspecified -> 0
    FileSize.Zero -> 1024 // libtorrent 没法禁用, 那就限速到 1KB/s
    else -> inBytes.toInt()
}

private fun computeTorrentFingerprint(
    versionCode: String = currentAniBuildConfig.versionCode,
): String = "-aniLT${versionCode}-"

private fun computeTorrentUserAgent(
    versionCode: String = currentAniBuildConfig.versionCode,
): String = "ani_libtorrent/${versionCode}"

private fun Flow<HttpClient>.asHttpFileDownloader(): HttpFileDownloader = object : HttpFileDownloader {
    override suspend fun download(url: String): ByteArray = first().get(url).readBytes()
    override fun close() {
    }

    override fun toString(): String {
        return "HttpClientAsHttpFileDownloader(client=$this@asHttpFileDownloader)"
    }
}
