/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScenePage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuite
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteDefaults
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteLayout
import me.him188.ani.app.ui.cache.CacheManagementPage
import me.him188.ani.app.ui.cache.CacheManagementViewModel
import me.him188.ani.app.ui.exploration.ExplorationPage
import me.him188.ani.app.ui.exploration.search.SearchPage
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.SharedTransitionKeys
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.isAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.subject.collection.CollectionPage
import me.him188.ani.app.ui.subject.collection.UserCollectionsViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsPage
import me.him188.ani.utils.platform.isAndroid
import kotlin.coroutines.cancellation.CancellationException


@Composable
fun MainScene(
    page: MainScenePage,
    modifier: Modifier = Modifier,
    onNavigateToPage: (MainScenePage) -> Unit,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets, // Compose for Desktop 目前不会考虑这个
    navigationLayoutType: NavigationSuiteType = AniNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo(),
    ),
) {
    if (LocalPlatform.current.isAndroid()) {
        val context = LocalContext.current
        val window = LocalPlatformWindow.current
        LaunchedEffect(true) {
            context.setRequestFullScreen(window, false)
        }
    }

    MainSceneContent(page, windowInsets, onNavigateToPage, modifier, navigationLayoutType)
}

@Composable
private fun MainSceneContent(
    page: MainScenePage,
    windowInsets: WindowInsets,
    onNavigateToPage: (MainScenePage) -> Unit,
    modifier: Modifier = Modifier,
    navigationLayoutType: NavigationSuiteType = AniNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo(),
    ),
) {
    AniNavigationSuiteLayout(
        navigationSuite = {
            AniNavigationSuite(
                windowInsets,
                navigationRailHeader = {
                    FloatingActionButton(
                        { onNavigateToPage(MainScenePage.Search) },
                        Modifier
                            .desktopTitleBarPadding()
                            .ifThen(currentWindowAdaptiveInfo().windowSizeClass.windowHeightSizeClass.isAtLeastMedium) {
                                // 移动端横屏不增加额外 padding
                                padding(vertical = 48.dp)
                            },
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    ) {
                        Icon(Icons.Rounded.Search, "搜索")
                    }
                },
                colors = NavigationSuiteDefaults.colors(
                    navigationDrawerContainerColor = AniThemeDefaults.navigationContainerColor,
                    navigationBarContainerColor = AniThemeDefaults.navigationContainerColor,
                    navigationRailContainerColor = AniThemeDefaults.navigationContainerColor,
                ),
                navigationRailItemSpacing = 8.dp,
                layoutType = navigationLayoutType,
            ) {
                for (entry in MainScenePage.visibleEntries) {
                    item(
                        page == entry,
                        onClick = { onNavigateToPage(entry) },
                        icon = { Icon(entry.getIcon(), null) },
                        label = { Text(text = entry.getText()) },
                    )
                }
            }
        },
        modifier,
        layoutType = navigationLayoutType,
    ) {
        val navigator by rememberUpdatedState(LocalNavigator.current)
        AnimatedContent(
            page,
            Modifier.fillMaxSize(),
            transitionSpec = {
//                val easing = CubicBezierEasing(0f, 0f, 1f, 1f)
//                val fadeIn = fadeIn(tween(25, easing = easing))
//                val fadeOut = fadeOut(tween(25, easing = easing))
//                fadeIn togetherWith fadeOut
                fadeIn(snap()) togetherWith fadeOut(snap())
            },
        ) { page ->
            TabContent(layoutType = navigationLayoutType) {
                when (page) {
                    MainScenePage.Exploration -> {
                        ExplorationPage(
                            viewModel { ExplorationPageViewModel() }.explorationPageState,
                            onSearch = { onNavigateToPage(MainScenePage.Search) },
                            onClickSettings = { navigator.navigateSettings() },
                            modifier.fillMaxSize(),
                            windowInsets = windowInsets,
                        )
                    }

                    MainScenePage.Collection -> {
                        val vm = viewModel<UserCollectionsViewModel> { UserCollectionsViewModel() }
                        CollectionPage(
                            state = vm.state,
                            items = vm.items,
                            windowInsets = windowInsets,
                            onClickSearch = { onNavigateToPage(MainScenePage.Search) },
                            onClickSettings = { navigator.navigateSettings() },
                            Modifier.fillMaxSize(),
                            enableAnimation = vm.myCollectionsSettings.enableListAnimation1,
                            lazyGridState = vm.lazyGridState,
                        )
                    }

                    MainScenePage.CacheManagement -> CacheManagementPage(
                        viewModel { CacheManagementViewModel(navigator) },
                        showBack = false,
                        Modifier.fillMaxSize(),
                        windowInsets = windowInsets,
                    )

                    MainScenePage.Search -> {
                        val vm = viewModel { SearchViewModel() }
                        BackHandler(true) {
                            onNavigateToPage(MainScenePage.Exploration)
                        }
                        val listDetailNavigator = rememberListDetailPaneScaffoldNavigator()
                        val scope = rememberCoroutineScope()
                        val toaster = LocalToaster.current
                        SearchPage(
                            vm.searchPageState,
                            windowInsets,
                            detailContent = {
                                vm.subjectDetailsStateLoader.subjectDetailsStateFlow?.let { stateFlow ->
                                    val state by stateFlow.collectAsStateWithLifecycle()
                                    SubjectDetailsPage(
                                        state,
                                        onPlay = { episodeId ->
                                            navigator.navigateEpisodeDetails(
                                                state.info.subjectId,
                                                episodeId,
                                            )
                                        },
                                        animatedVisibilityScope,
                                        Modifier.ifThen(listDetailLayoutParameters.isSinglePane) {
                                            sharedElement(
                                                rememberSharedContentState(
                                                    SharedTransitionKeys.subjectBounds(
                                                        state.info.subjectId,
                                                    ),
                                                ),
                                                animatedVisibilityScope,
                                            )
                                        },
                                    )
                                }
                            },
                            Modifier.fillMaxSize(),
                            onSelect = { index, item ->
                                vm.searchPageState.selectedItemIndex = index
                                scope.launch {
                                    try {
                                        vm.viewSubjectDetails(item.subjectId) // 加载完成后才切换, 否则 shared transition 会黑屏一小会
                                        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        toaster.toast("加载失败: ${e.message}")
                                        throw e
                                    }
                                }
                            },
                            navigator = listDetailNavigator,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TabContent(
    layoutType: NavigationSuiteType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = when (layoutType) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.None -> RectangleShape

        NavigationSuiteType.NavigationRail,
        NavigationSuiteType.NavigationDrawer -> MaterialTheme.shapes.extraLarge.copy(
            topEnd = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )

        else -> RectangleShape
    }
    Surface(
        modifier.clip(shape),
        shape = shape,
        color = AniThemeDefaults.pageContentBackgroundColor,
    ) {
        content()
    }
}
