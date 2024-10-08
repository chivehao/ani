package me.him188.ani.datasources.ikaros

import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceParameters
import me.him188.ani.datasources.api.source.MediaSourceParametersBuilder
import me.him188.ani.datasources.api.source.get
import me.him188.ani.datasources.api.source.useHttpClient
import java.nio.charset.StandardCharsets
import java.util.Base64

class IkarosMediaSource(config: MediaSourceConfig) : HttpMediaSource() {
    companion object {
        const val ID = "ikaros"
    }

    internal val client = IkarosClient(
        config[Parameters.baseUrl],
        useHttpClient(config) {
            defaultRequest {
                val username = config[Parameters.username]
                val password = config[Parameters.password]
                header(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder()
                        .encodeToString(
                            "$username:$password".toByteArray(
                                StandardCharsets.UTF_8,
                            ),
                        ),
                )
            }
        },
    )

    object Parameters : MediaSourceParametersBuilder() {
        val baseUrl = string("baseUrl", description = "API base URL")
        val username = string("username", description = "用户名")
        val password = string("password", description = "密码")
    }

    class Factory : MediaSourceFactory {
        override val mediaSourceId: String get() = ID
        override val parameters: MediaSourceParameters = Parameters.build()
        override val allowMultipleInstances: Boolean get() = true
        override fun create(config: MediaSourceConfig): MediaSource = IkarosMediaSource(config)
    }

    override val kind: MediaSourceKind get() = MediaSourceKind.WEB

    override val mediaSourceId: String get() = ID

    override suspend fun checkConnection(): ConnectionStatus {
        return if ((HttpStatusCode.OK == client.checkConnection())
        ) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        val subjectId = checkNotNull(query.subjectId)
        val episodeSort = checkNotNull(query.episodeSort)
        val ikarosSubjectDetails = checkNotNull(client.postSubjectSyncBgmTv(subjectId))
        return client.subjectDetails2SizedSource(ikarosSubjectDetails, episodeSort)
    }
}
