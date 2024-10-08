package me.him188.ani.app.ui.subject.cache

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.source.media.cache.EpisodeCacheStatus
import me.him188.ani.app.data.source.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.data.source.media.selector.MediaSelectorFactory
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.coroutines.EmptyCoroutineContext

@Composable
@Preview
private fun PreviewEpisodeCacheActionIcon() {
    ProvideCompositionLocalsForPreview {
        Column(Modifier.width(IntrinsicSize.Min)) {
            Text(text = "isLoadingIndefinitely")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = true,
                    hasActionRunning = true,
                    cacheStatus = null,
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()

            Text(text = "NotCached")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = false,
                    hasActionRunning = true,
                    cacheStatus = EpisodeCacheStatus.NotCached,
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()

            Text(text = "Caching - progress null")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = false,
                    hasActionRunning = true,
                    cacheStatus = EpisodeCacheStatus.Caching(
                        null,
                        me.him188.ani.datasources.api.topic.FileSize.Unspecified,
                    ),
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()

            Text(text = "Caching - progress not null")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = false,
                    hasActionRunning = true,
                    cacheStatus = EpisodeCacheStatus.Caching(
                        0.3f,
                        me.him188.ani.datasources.api.topic.FileSize.Unspecified,
                    ),
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()
        }
    }
}


@Composable
@Preview
private fun PreviewEpisodeCacheActionIconHasActionRunningChange() {
    ProvideCompositionLocalsForPreview {
        var running by remember {
            mutableStateOf(false)
        }
        Column {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = true,
                    hasActionRunning = running,
                    cacheStatus = null,
                    canCache = true,
                    onClick = { },
                    onCancel = { running = false },
                )

            }
            Button(onClick = { running = true }) {
                Text(text = "running = true")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewEpisodeItem() = ProvideCompositionLocalsForPreview {
    SettingsTab {
        var id = 0
        listOf(true, false).forEach { hasPublished ->
            UnifiedCollectionType.entries.forEach { watchStatus ->
                EpisodeCacheItem(
                    episode = remember {
                        EpisodeCacheState(
                            0,
                            { EpisodeCacheRequester(flowOf(), MediaSelectorFactory.withKoin(), flowOf()) },
                            info = MutableStateFlow(
                                EpisodeCacheInfo(
                                    sort = EpisodeSort(++id),
                                    ep = null,
                                    title = "$watchStatus - ${if (hasPublished) "已开播" else "未开播"}",
                                    watchStatus = watchStatus,
                                    hasPublished = true,
                                ),
                            ),
                            cacheStatusFlow = MutableStateFlow(EpisodeCacheStatus.NotCached),
                            EmptyCoroutineContext,
                        )
                    },
                    onClick = {},
                    isRequestHidden = false,
                    dropdown = { },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewEpisodeItemVeryLong() = ProvideCompositionLocalsForPreview {
    SettingsTab {
        EpisodeCacheItem(
            remember {
                EpisodeCacheState(
                    0,
                    { EpisodeCacheRequester(flowOf(), MediaSelectorFactory.withKoin(), flowOf()) },
                    info = MutableStateFlow(
                        EpisodeCacheInfo(
                            sort = EpisodeSort(1),
                            ep = null,
                            title = "测试标题".repeat(10),
                            watchStatus = UnifiedCollectionType.WISH,
                            hasPublished = true,
                        ),
                    ),
                    cacheStatusFlow = MutableStateFlow(EpisodeCacheStatus.NotCached),
                    EmptyCoroutineContext,
                )
            },
            onClick = {},
            isRequestHidden = false,
            dropdown = { },
        )
    }
}
