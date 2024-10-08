package me.him188.ani.app.torrent.anitorrent.session

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.torrent.anitorrent.DisposableTaskQueue
import me.him188.ani.app.torrent.api.TorrentDownloadSession
import me.him188.ani.app.torrent.api.files.AbstractTorrentFileEntry
import me.him188.ani.app.torrent.api.files.DownloadStats
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.PieceState
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.TorrentFilePieceMatcher
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PiecePriorities
import me.him188.ani.app.torrent.api.pieces.TorrentDownloadController
import me.him188.ani.app.torrent.api.pieces.lastIndex
import me.him188.ani.app.torrent.api.pieces.startIndex
import me.him188.ani.app.torrent.io.TorrentInput
import me.him188.ani.utils.coroutines.runInterruptible
import me.him188.ani.utils.io.SeekableInput
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

class AnitorrentDownloadSession(
    private val handle: TorrentHandle,
    private val saveDirectory: SystemPath,
    private val fastResumeFile: SystemPath,
    private val onClose: (AnitorrentDownloadSession) -> Unit,
    private val onPostClose: (AnitorrentDownloadSession) -> Unit,
    private val onDelete: (AnitorrentDownloadSession) -> Unit,
    parentCoroutineContext: CoroutineContext,
    dispatcher: CoroutineContext = Dispatchers.IO,
) : TorrentDownloadSession, SynchronizedObject() {
    val logger = logger(this::class)
    val handleId get() = handle.id // 内存地址, 不可持久

    private val scope = CoroutineScope(
        parentCoroutineContext + dispatcher + SupervisorJob(parentCoroutineContext[Job]),
    )

    init {
        scope.launch {
            while (isActive) {
                if (!handle.isValid) {
                    return@launch
                }
                handle.postStatusUpdates()
                delay(1000)
            }
        }
    }

    override val overallStats: MutableDownloadStats = MutableDownloadStats()

    private val openFiles = mutableListOf<AnitorrentEntry.EntryHandle>()
    private val prioritizer = createPiecePriorities()

    inner class AnitorrentEntry(
        override val pieces: List<Piece>,
        index: Int,
        val offset: Long,
        length: Long, saveDirectory: SystemPath, relativePath: String,
        isDebug: Boolean, parentCoroutineContext: CoroutineContext,
        initialDownloadedBytes: Long,
    ) : AbstractTorrentFileEntry(
        index, length, saveDirectory, relativePath, handleId.toString(), isDebug,
        parentCoroutineContext,
    ) {
        override val supportsStreaming: Boolean get() = true
        val pieceRange = if (pieces.isEmpty()) LongRange.EMPTY else pieces.first().startIndex..pieces.last().lastIndex

        val controller: TorrentDownloadController = TorrentDownloadController(
            pieces,
            prioritizer,
            // libtorrent 可能会平均地请求整个 window, 所以不能太大
            windowSize = (8 * 1024 * 1024 / (pieces.firstOrNull()?.size ?: 1024L)).toInt().coerceIn(2, 64),
            headerSize = 2 * 1024 * 1024,
            footerSize = (0.5 * 1024 * 1024).toLong(),
            possibleFooterSize = 8 * 1024 * 1024,
        )

        inner class EntryHandle : AbstractTorrentFileHandle() {
            override val entry get() = this@AnitorrentEntry

            override suspend fun closeImpl() {
                openFiles.remove(this)
                closeIfNotInUse()
            }

            override fun resumeImpl(priority: FilePriority) {
                controller.onTorrentResumed()
                handle.resume()
            }

            override suspend fun closeAndDelete() {
                close()
                deleteEntireTorrentIfNotInUse()
            }
        }

        override fun updatePriority() {
            logger.info { "[$handleId] Set file priority to $requestingPriority: $relativePath" }
            handle.setFilePriority(index, requestingPriority)
        }

        val downloadedBytes: MutableStateFlow<Long> = MutableStateFlow(initialDownloadedBytes)
        override val stats: DownloadStats = object : DownloadStats() {
            override val totalSize: MutableStateFlow<Long> = MutableStateFlow(length)
            override val downloadedBytes: MutableStateFlow<Long> get() = this@AnitorrentEntry.downloadedBytes
            override val uploadRate: MutableStateFlow<Long> get() = this@AnitorrentDownloadSession.overallStats.uploadRate
            override val progress: Flow<Float> =
                combine(totalSize, downloadedBytes) { total, downloaded ->
                    if (total == 0L) return@combine 0f
                    downloaded.toFloat() / total.toFloat()
                }
        }

        override fun createHandle(): TorrentFileHandle {
            return EntryHandle().also {
                openFiles.add(it)
            }
        }

        override suspend fun createInput(): SeekableInput {
            val input = resolveFileOrNull() ?: resolveDownloadingFile()
            return runInterruptible(Dispatchers.IO) {
                TorrentInput(
                    input,
                    this.pieces,
                    logicalStartOffset = offset,
                    onWait = { piece ->
                        updatePieceDeadlinesForSeek(piece)
                    },
                    size = length,
                )
            }
        }

        private fun updatePieceDeadlinesForSeek(requested: Piece) {
            if (!controller.isDownloading(requested.pieceIndex)) {
                logger.info { "[TorrentDownloadControl] $torrentId: Resetting deadlines to download ${requested.pieceIndex}" }
                handle.clearPieceDeadlines()
                controller.onSeek(requested.pieceIndex) // will request further pieces
            } else {
                logger.info { "[TorrentDownloadControl] $torrentId: Requested piece ${requested.pieceIndex} is already downloading" }
                return
            }
        }
    }

    /**
     * 延迟获取的具体文件信息. 因为如果是添加磁力链的话, 文件信息需要经过耗时的网络查询后才能得到.
     * @see useTorrentInfoOrLaunch
     */
    inner class TorrentInfo(
        val name: String,
        val allPiecesInTorrent: List<Piece>,
        val entries: List<AnitorrentEntry>,
    ) {
        init {
            check(allPiecesInTorrent is RandomAccess)
        }

        override fun toString(): String {
            return "TorrentInfo(name=$name, numPieces=${allPiecesInTorrent.size}, entries.size=${entries.size})"
        }
    }

    private val actualTorrentInfo = CompletableDeferred<TorrentInfo>()

    /**
     * 在某些时候 [close] 需要等待此 session 完全关闭，通过 [event_listener_t.on_torrent_removed] 来监听此事件
     */
    private val closingDeferred: CompletableDeferred<Unit> by lazy { CompletableDeferred() }

    /**
     * 当 [actualTorrentInfo] 还未完成时的任务队列, 用于延迟执行需要 [TorrentInfo] 的任务.
     *
     * 这是因为 [onTorrentFinished] 和 [onPieceDownloading] 可能会早于 [onTorrentChecked] 调用.
     * 而且 [onPieceDownloading] 会非常频繁调用, 不能为它启动过多协程
     */
    private val taskQueue: DisposableTaskQueue<AnitorrentDownloadSession> = DisposableTaskQueue(this).apply {
        scope.launch {
            actualTorrentInfo.await()
            runAndDispose()
        }
    }

    // 回调可能会早于 [actualTorrentInfo] 计算完成之前调用, 所以需要考虑延迟的情况
    private inline fun useTorrentInfoOrLaunch(
        // receiver 是为了让 lambda 无需捕获 this 对象. 因为 [onPieceDownloading] 可能会调用数万次
        // will be inlined multiple times!
        crossinline block: AnitorrentDownloadSession.(TorrentInfo) -> Unit
    ) {
        if (actualTorrentInfo.isCompleted) {
            block(actualTorrentInfo.getCompleted())
        } else {
            val added = taskQueue.add {
                block(actualTorrentInfo.getCompleted())
            }
            if (!added) {
                // taskQueue disposed, then actualTorrentInfo must have completed now
                check(actualTorrentInfo.isCompleted) {
                    "taskQueue disposed however actualTorrentInfo is not completed yet"
                }
                block(actualTorrentInfo.getCompleted())
            }
        }
    }

    private fun initializeTorrentInfo(info: TorrentDescriptor) {
        check(this.actualTorrentInfo.isActive) {
            "actualTorrentInfo has already been completed or closed"
        }
        logger.info { "initializeTorrentInfo" }
        val allPiecesInTorrent =
            Piece.buildPieces(info.numPieces) {
                if (it == info.numPieces - 1) {
                    info.lastPieceSize
                } else info.pieceLength
            }

        val entries: List<AnitorrentEntry> = kotlin.run {
            val numFiles = info.fileSequence.toList()

            var currentOffset = 0L
            val list = numFiles.mapIndexed { index, file ->
                val size = file.size
                val path = file.path.takeIf { it.isNotBlank() } ?: file.name
                val list = TorrentFilePieceMatcher.matchPiecesForFile(
                    allPiecesInTorrent,
                    currentOffset,
                    size,
                ).also { pieces ->
                    logPieces(pieces, path)
                }
                val filePieces = if (list is RandomAccess) {
                    list
                } else {
                    ArrayList(list)
                }
                AnitorrentEntry(
                    pieces = filePieces,
                    index = index,
                    offset = currentOffset,
                    length = size,
                    saveDirectory = saveDirectory,
                    relativePath = path,
                    isDebug = false,
                    parentCoroutineContext = Dispatchers.IO,
                    initialDownloadedBytes = calculateTotalFinishedSize(filePieces).coerceAtMost(size),
                ).also {
                    currentOffset += size
                }
            }
            list
        }
        val value = TorrentInfo(
            name = info.name,
            allPiecesInTorrent,
            entries,
        )
        logger.info { "[$handleId] Got torrent info: $value" }
        this.overallStats.totalSize.value = entries.sumOf { it.length }
//        handle.ignore_all_files() // no need because we set libtorrent::torrent_flags::default_dont_download in native
        this.actualTorrentInfo.complete(value)
    }

    fun onTorrentChecked() {
        logger.info { "[$handleId] onTorrentChecked" }
        reloadFilesAndInitializeIfNotYet()
    }

    private fun reloadFilesAndInitializeIfNotYet() {
        if (!actualTorrentInfo.isCompleted) {
            logger.info { "[$handleId] reloadFiles" }
            initializeTorrentInfo(
                handle.reloadFile(),
            ) // split to multiple lines for debugging
        }
    }

    fun onPieceDownloading(pieceIndex: Int) {
        useTorrentInfoOrLaunch { info ->
            info.allPiecesInTorrent.getOrNull(pieceIndex)?.state?.compareAndSet(
                PieceState.READY,
                PieceState.DOWNLOADING,
            )
        }
    }

    fun onPieceFinished(pieceIndex: Int) {
        // 注意, 在恢复时, libtorrent 不一定会为所有 piece 发送这个事件
        useTorrentInfoOrLaunch { info ->
            onPieceFinishedImpl(info, pieceIndex) // avoid being inlined multiple times
        }
    }

    private fun onPieceFinishedImpl(
        info: TorrentInfo,
        pieceIndex: Int
    ) {
        info.allPiecesInTorrent.getOrNull(pieceIndex)?.state?.value = PieceState.FINISHED
        for (file in openFiles) {
            if (pieceIndex in file.entry.pieceRange) {
                file.entry.controller.onPieceDownloaded(pieceIndex)
            }
        }
        // TODO: Anitorrent 计算 file 完成度的算法需要优化性能, 这有 n^2 复杂度
        for (entry in info.entries) {
            if (pieceIndex !in entry.pieceRange) continue

            val downloadedBytes = entry.downloadedBytes.value
            if (downloadedBytes == entry.length) {
                // entry already finished
                continue
            }

            entry.downloadedBytes.compareAndSet(
                downloadedBytes,
                calculateTotalFinishedSize(entry.pieces).coerceAtMost(entry.length),
            ) // lazy set, if already finished, don't update
        }
    }

    fun onTorrentFinished() {
        // 注意, 这个事件不一定是所有文件下载完成了. 
        // 在刚刚创建任务的时候所有文件都是完全不下载的状态, libtorrent 会立即广播这个事件.
        logger.info { "[$handleId] onTorrentFinished" }
        reloadFilesAndInitializeIfNotYet()
        handle.postSaveResume()
    }

    fun onStatsUpdate(stats: TorrentStats) {
        this.overallStats.downloadRate.value = stats.downloadPayloadRate.toUInt().toLong()
        this.overallStats.uploadRate.value = stats.uploadPayloadRate.toUInt().toLong()
        this.overallStats.progress.value = stats.progress
        this.overallStats.downloadedBytes.value = (this.overallStats.totalSize.value * stats.progress).toLong()
        this.overallStats.uploadedBytes.value = stats.totalPayloadUpload
        this.overallStats.isFinished.value = stats.progress >= 1f
    }

    fun onFileCompleted(index: Int) {
        useTorrentInfoOrLaunch { info ->
            val entry = info.entries.getOrNull(index) ?: return@useTorrentInfoOrLaunch
            // 没有设置 pieces 状态, 因为假如首尾 pieces 不是精确匹配, 首尾 pieces 可能实际上没有完成
            entry.downloadedBytes.value = entry.length
        }
    }

    fun onSaveResumeData(data: TorrentResumeData) {
        logger.info { "[$handleId] saving resume data to: ${fastResumeFile.absolutePath}" }
        data.saveToPath(fastResumeFile.path)
    }

    override suspend fun getName(): String = this.actualTorrentInfo.await().name

    override suspend fun getFiles(): List<TorrentFileEntry> = this.actualTorrentInfo.await().entries

    @Volatile
    private var closed = false

    override suspend fun close() {
        if (closed) { // 多次调用此 close 会等待同一个 deferred, 完成时一起返回
            closingDeferred.await()
            return
        }

        kotlin.run {
            synchronized(this) {
                if (closed) return@synchronized // 多次调用此 close 会等待同一个 deferred, 完成时一起返回
                closed = true
                return@run // set close = true, 跳出 run lambda 并真正执行 onClose() 并等待
            }
            closingDeferred.await() // 只有在 synchronized 里检查 closed == true 时会在此 await
            return
        }

        logger.info { "[$handleId] closing" }
        onClose(this)
        try {
            withTimeout(7500L) {
                closingDeferred.await() // 收到 on_torrent_removed 事件时返回
            }
        } catch (timeout: TimeoutCancellationException) {
            logger.warn { "[$handleId] timeout on closing this session, force to mark as closed." }
            closingDeferred.complete(Unit)
        }
        scope.cancel()
        onPostClose(this)
    }

    fun onTorrentRemoved() {
        logger.info { "[$handleId] onTorrentRemoved" }
        closingDeferred.complete(Unit)
    }

    override suspend fun closeIfNotInUse() {
        if (openFiles.isEmpty()) {
            close()
        }
    }

    fun deleteEntireTorrentIfNotInUse() {
        if (openFiles.isEmpty() && closed) {
            saveDirectory.deleteRecursively()
            onDelete(this)
        }
    }

    private fun createPiecePriorities(): PiecePriorities {
        return object : PiecePriorities {
            //            private val priorities = Array(torrentFile().numPieces()) { Priority.IGNORE }
            override fun downloadOnly(pieceIndexes: List<Int>, possibleFooterRange: IntRange) {
                if (pieceIndexes.isEmpty()) {
                    return
                }
                logger.debug { "[$handleId][TorrentDownloadControl] Prioritizing pieces: $pieceIndexes" }
                val smallestIndex = pieceIndexes.minBy { it }

                // 超高优先下载第一个 piece, 防止它一直请求后面的 (因为一旦有 piece 完成, window 就会往后变大)
                handle.setPieceDeadline(pieceIndexes.first(), -10000)

                for (i in 1 until pieceIndexes.size) {
                    val pieceIndex = pieceIndexes[i]
                    handle.setPieceDeadline(
                        pieceIndex,
                        // 低于现在可以让 libtorrent 更急
                        -5000 + if (pieceIndex in possibleFooterRange) {
                            // 对于视频尾部元数据, 同样需要给予较高的优先级
                            val lastFooter = possibleFooterRange.last()
                            (lastFooter - pieceIndex) * 700
                        } else {
                            // 最高优先级下载第一个. 第一个有可能会是 seek 之后的.
                            (pieceIndex - smallestIndex) * 700
                        },
                    )
                }
            }
        }
    }
}


private fun AnitorrentDownloadSession.logPieces(pieces: List<Piece>, pathInTorrent: String) {
    logger.info {
        val start = pieces.minByOrNull { it.startIndex }
        val end = pieces.maxByOrNull { it.lastIndex }
        if (start == null || end == null) {
            "[$handleId] File '$pathInTorrent' piece initialized, ${pieces.size} pieces, " +
                    "index range: start=$start, end=$end"
        } else {
            "[$handleId] File '$pathInTorrent' piece initialized, ${pieces.size} pieces, " +
                    "index range: ${start.pieceIndex..end.pieceIndex}, " +
                    "offset range: $start..$end"
        }
    }
}

val TorrentDescriptor.fileSequence
    get() = sequence {
        val fileCount = fileCount
        repeat(fileCount) {
            val file = fileAtOrNull(it)
            checkNotNull(file) { "fileAtOrNull($it) returned null, however fileCount was $fileCount" }
            yield(file)
        }
    }

private fun calculateTotalFinishedSize(pieces: List<Piece>): Long =
    pieces.sumOf { if (it.state.value == PieceState.FINISHED) it.size else 0 }
