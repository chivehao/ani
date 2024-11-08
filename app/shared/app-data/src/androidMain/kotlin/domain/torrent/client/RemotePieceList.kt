/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IPieceStateObserver
import me.him188.ani.app.domain.torrent.IRemotePieceList
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.resume

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemotePieceList(
    getRemote: () -> IRemotePieceList,
) : PieceList(
    getRemote().immutableSizeArray,
    getRemote().immutableDataOffsetArray,
    getRemote().immutableInitialPieceIndex,
), RemoteCall<IRemotePieceList> by RetryRemoteCall(getRemote) {
    private val logger = logger<RemotePieceList>()

    private val pieceStateSharedMem by lazy { call { pieceStateArrayMemRegion } }
    private val pieceStateBuf by lazy { pieceStateSharedMem.mapReadOnly() }

    override var Piece.state: PieceState
        get() = PIECE_STATE_ENTRIES[pieceStateBuf.get(indexInList).toInt()]
        set(_) {
            throw UnsupportedOperationException("set Piece state is not allowed in remote PieceList")
        }

    override fun Piece.compareAndSetState(expect: PieceState, update: PieceState): Boolean {
        throw UnsupportedOperationException("set Piece state is not allowed in remote PieceList")
    }

    override suspend fun Piece.awaitFinished() {
        if (state == PieceState.FINISHED) return

        var disposableHandle: IDisposableHandle? = null
        val readyState = try {
            suspendCancellableCoroutine { cont ->
                logger.info { "Awaiting state remote piece $pieceIndex to ${PieceState.FINISHED}." }
                // remote 必须保证 register observer 调用后一定可以监听到新的 state
                disposableHandle = call {
                    registerPieceStateObserver(
                        pieceIndex,
                        object : IPieceStateObserver.Stub() {
                            override fun onUpdate() {
                                val newState = state
                                if (newState == PieceState.FINISHED) {
                                    cont.resume(newState)
                                }
                            }
                        },
                    )
                }
                // 注册 listener 之后如果 state 是 ready 了，下面就监听不到 ready state 了
                if (state == PieceState.FINISHED) cont.resume(state)
            }
        } finally {
            logger.info { "Got state of remote piece $pieceIndex: $state." }
            disposableHandle?.callOnceOrNull { dispose() }
        }
        
        check(state == readyState) { "Remote state of piece $this is changed from READY to $state" }
    }
    
    fun dispose() {
        call { dispose() }
    }
    
    companion object {
        private val PIECE_STATE_ENTRIES: List<PieceState> = PieceState.entries
    }
}