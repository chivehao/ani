package me.him188.ani.app.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.SubjectComment
import me.him188.ani.app.tools.caching.LazyDataCache
import me.him188.ani.app.tools.caching.LazyDataCacheContext
import me.him188.ani.app.tools.caching.RefreshOrderPolicy
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.richtext.toUIBriefText
import me.him188.ani.app.ui.foundation.richtext.toUIRichElements
import me.him188.ani.app.ui.subject.components.comment.UIComment
import me.him188.ani.app.ui.subject.components.comment.UIRichText
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.utils.bbcode.BBCode
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * A tool to load and parse comment from source to ui object
 */
class CommentLoader<T>(
    source: Flow<LazyDataCache<T>>,
    coroutineContext: CoroutineContext,
    uiMapper: suspend CommentMapperContext.(T) -> UIComment,
) : HasBackgroundScope by BackgroundScope(coroutineContext) {
    private val cachedSource = source.shareInBackground()

    val list = cachedSource
        .flatMapLatest { it.cachedDataFlow }
        .map { list ->
            list.map { with(CommentMapperContext) { uiMapper(it) } }
                .sortedByDescending { it.createdAt }
        }

    val hasFinished = cachedSource.flatMapLatest { it.isCompleted }
    val sourceVersion = cachedSource.map { Any() }

    suspend fun reload() {
        cachedSource.first().refresh(RefreshOrderPolicy.REPLACE)
    }

    suspend fun loadMore() {
        cachedSource.first().requestMore()
    }

    companion object {
        /**
         * create comment loader of subject
         */
        fun createForSubject(
            subjectId: Flow<Int>,
            coroutineContext: CoroutineContext,
            subjectCommentSource: suspend LazyDataCacheContext.(Int) -> PagedSource<SubjectComment>
        ) = CommentLoader(
            source = subjectId.map { sid ->
                LazyDataCache(
                    createSource = { ApiResponse.success(subjectCommentSource(sid)) },
                    getKey = { it.id },
                    debugName = "subjectComment-$sid",
                )
            },
            coroutineContext = coroutineContext,
            uiMapper = { comment ->
                UIComment(
                    id = comment.id,
                    creator = comment.creator,
                    content = parseBBCode(comment.content),
                    createdAt = comment.updatedAt * 1000L,
                    reactions = emptyList(),
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = comment.rating,
                )
            },
        )

        /**
         * create comment loader of episode
         */
        fun createForEpisode(
            episodeId: Flow<Int>,
            coroutineContext: CoroutineContext,
            episodeCommentSource: suspend LazyDataCacheContext.(Int) -> PagedSource<EpisodeComment>
        ) = CommentLoader(
            source = episodeId.map { eid ->
                LazyDataCache(
                    createSource = { ApiResponse.success(episodeCommentSource(eid)) },
                    getKey = { it.id },
                    debugName = "episodeComment-$eid",
                )
            },
            coroutineContext = coroutineContext,
            uiMapper = { comment ->
                UIComment(
                    id = comment.id,
                    creator = comment.creator,
                    content = parseBBCode(comment.content),
                    createdAt = comment.createdAt * 1000L,
                    reactions = emptyList(),
                    briefReplies = comment.replies.take(3).map { reply ->
                        UIComment(
                            id = reply.id,
                            creator = reply.creator,
                            content = parseBBCodeAsReply(reply.content),
                            createdAt = reply.createdAt * 1000L,
                            reactions = emptyList(),
                            briefReplies = emptyList(),
                            replyCount = 0,
                            rating = null,
                        )
                    },
                    replyCount = comment.replies.size,
                    rating = null,
                )
            },
        )
    }
}

object CommentMapperContext {
    suspend fun parseBBCode(code: String): UIRichText = suspendCancellableCoroutine { cont ->
        val richText = try {
            BBCode.parse(code)
        } catch (ex: Exception) {
            cont.cancel()
            return@suspendCancellableCoroutine
        }
        cont.resume(UIRichText(richText.toUIRichElements()))
    }

    suspend fun parseBBCodeAsReply(code: String): UIRichText = suspendCancellableCoroutine { cont ->
        val richText = try {
            BBCode.parse(code)
        } catch (ex: Exception) {
            cont.cancel()
            return@suspendCancellableCoroutine
        }

        listOf(richText.toUIBriefText().copy(maxLine = 2))
            .also { cont.resume(UIRichText(it)) }
    }
}