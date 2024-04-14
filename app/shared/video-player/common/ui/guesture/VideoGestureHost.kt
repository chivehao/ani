package me.him188.ani.app.videoplayer.ui.guesture

import androidx.annotation.UiThread
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.StreamType
import me.him188.ani.app.platform.getComponentAccessors
import me.him188.ani.app.tools.rememberMonoTasker
import me.him188.ani.app.ui.theme.aniDarkColorTheme
import me.him188.ani.app.videoplayer.ui.guesture.SwipeSeekerState.Companion.swipeToSeek
import me.him188.ani.datasources.bangumi.processing.fixToString
import kotlin.math.absoluteValue

@Stable
private fun renderTime(seconds: Int): String {
    return "${(seconds / 60).fixToString(2)}:${(seconds % 60).fixToString(2)}"
}

@Composable
fun rememberGestureIndicatorState(): GestureIndicatorState = remember { GestureIndicatorState() }

@Stable
class GestureIndicatorState {
    internal enum class State {
        PAUSED_ONCE,
        RESUMED_ONCE,
        VOLUME,
        BRIGHTNESS,
        SEEKING,
    }

    internal var visible: Boolean by mutableStateOf(false)
    internal var state: State? by mutableStateOf(null)
    internal var progressValue: Float by mutableFloatStateOf(0f)
    internal var deltaSeconds: Int by mutableIntStateOf(0)
    private var counter: Int = 0

    private inline fun show(
        state: State,
        setup: () -> Unit = {},
        action: () -> Unit
    ) {
        val ticket = ++counter
        try {
            setup()
            this.state = state
            visible = true
            action()
        } finally {
            if (this.counter == ticket && // no one changed the state after us
                this.state == state
            ) {
                visible = false
            }
        }
    }

    private companion object {
        private const val LONG: Long = 700
        private const val SHORT: Long = 500
    }

    @UiThread
    suspend fun showPausedLong() {
        show(State.PAUSED_ONCE) {
            delay(LONG)
        }
    }

    @UiThread
    suspend fun showResumedLong() {
        show(State.RESUMED_ONCE) {
            delay(LONG)
        }
    }

    @UiThread
    suspend fun showVolumeRange(currentRatio: Float) {
        show(State.VOLUME, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showBrightnessRange(currentRatio: Float) {
        show(State.BRIGHTNESS, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showSeeking(
        deltaSeconds: Int,
    ) {
        show(State.SEEKING, setup = { this.deltaSeconds = deltaSeconds }) {
            delay(SHORT)
        }
    }
}

/**
 * 展示当前快进/快退秒数的指示器.
 *
 * `<< 00:00` / `>> 00:00`
 */
@Composable
fun GestureIndicator(
    state: GestureIndicatorState,
) {
    val shape = MaterialTheme.shapes.small
    val colors = aniDarkColorTheme()
    var lastDelta by remember {
        mutableIntStateOf(state.deltaSeconds)
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(tween(durationMillis = 500)),
        label = "SeekPositionIndicator"
    ) {
        Surface(
            Modifier.alpha(0.8f),
            color = colors.surface,
            shape = shape,
            shadowElevation = 1.dp,
            contentColor = colors.onSurface,
        ) {
            val iconSize = 36.dp
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) {
                Row(
                    Modifier.background(Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(iconSize),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Used by volume and brightness
                    val progressIndicator: @Composable () -> Unit = remember(state, colors) {
                        // This remember is needed because Compose does not remember lambdas 
                        // and can cause performance problem in this fast-changing composable.
                        {
                            LinearProgressIndicator(
                                progress = { state.progressValue },
                                modifier = Modifier.width(80.dp),
                                color = colors.primary,
                                trackColor = colors.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }

                    when (state.state) {
                        GestureIndicatorState.State.RESUMED_ONCE -> {
                            Icon(
                                Icons.Rounded.PlayArrow, null,
                                Modifier.size(iconSize).background(Color.Transparent)
                            )
                        }

                        GestureIndicatorState.State.PAUSED_ONCE -> {
                            Icon(Icons.Rounded.Pause, null, Modifier.size(iconSize))
                        }

                        GestureIndicatorState.State.SEEKING -> {
                            val deltaDuration = state.deltaSeconds
                            // 记忆变为 0 之前的 delta, 这样在快进/快退结束后, 会显示上一次的 delta, 而不是显示 0
                            val duration = if (deltaDuration == 0) {
                                lastDelta
                            } else {
                                deltaDuration.also {
                                    lastDelta = deltaDuration
                                }
                            }

                            Icon(
                                if (duration > 0) {
                                    Icons.Rounded.FastForward
                                } else {
                                    Icons.Rounded.FastRewind
                                },
                                null,
                                Modifier.size(iconSize)
                            )
                            val text = renderTime(duration.absoluteValue)
                            Text(
                                text,
                                maxLines = 1,
                            )
                        }

                        GestureIndicatorState.State.VOLUME -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp, null,
                                Modifier.size(iconSize)
                            )
                            progressIndicator()
                        }

                        GestureIndicatorState.State.BRIGHTNESS -> {
                            Icon(
                                when (state.progressValue) {
                                    in 0.67..1.0 -> Icons.Rounded.BrightnessHigh
                                    in 0.33..0.67 -> Icons.Rounded.BrightnessMedium
                                    else -> Icons.Rounded.BrightnessLow
                                }, null,
                                Modifier.size(iconSize)
                            )
                            progressIndicator()
                        }

                        null -> {}
                    }
                }
            }
        }
    }
}


@Composable
fun VideoGestureHost(
    seekerState: SwipeSeekerState,
    indicatorState: GestureIndicatorState,
    modifier: Modifier = Modifier,
    onClickScreen: () -> Unit = {},
    onDoubleClickScreen: () -> Unit = {},
) {
    BoxWithConstraints {
        Row(Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
            LaunchedEffect(seekerState.deltaSeconds) {
                if (seekerState.isSeeking) {
                    indicatorState.showSeeking(seekerState.deltaSeconds)
                }
            }
            MaterialTheme(aniDarkColorTheme()) {
                GestureIndicator(indicatorState)
            }
        }
        val maxHeight = maxHeight


        val context by rememberUpdatedState(LocalContext.current)
        val audioController by remember {
            derivedStateOf {
                getComponentAccessors(context = context).audioManager?.asLevelController(StreamType.MUSIC)
            }
        }
        val brightnessLevelController by remember {
            derivedStateOf {
                getComponentAccessors(context = context).brightnessManager?.asLevelController()
            }
        }

        val indicatorTasker = rememberMonoTasker()

        Box(
            modifier
                .combinedClickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClickScreen,
                    onDoubleClick = onDoubleClickScreen,
                )
                .swipeToSeek(seekerState, Orientation.Horizontal)
                .fillMaxSize()
        ) {
            Row(Modifier.matchParentSize()) {
                Box(
                    Modifier.then(
                        brightnessLevelController?.let { controller ->
                            Modifier.swipeLevelControl(
                                controller,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                afterStep = {
                                    indicatorTasker.launch {
                                        indicatorState.showBrightnessRange(controller.level)
                                    }
                                }
                            )
                        } ?: Modifier,
                    ).weight(1f).fillMaxHeight()
                )

                Box(Modifier.weight(1f).fillMaxHeight())

                Box(Modifier.then(
                    audioController?.let { controller ->
                        Modifier.swipeLevelControl(
                            controller,
                            ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                            Orientation.Vertical,
                            afterStep = {
                                indicatorTasker.launch {
                                    indicatorState.showVolumeRange(controller.level)
                                }
                            }
                        )
                    } ?: Modifier
                ).weight(1f).fillMaxHeight())
            }
        }
    }
}
