package com.ivy.legacy.arkui

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 明日方舟式动效工具（PRTS Design motion：80/150/250/400ms，
 * cubic-bezier(0.2,0.8,0.2,1)——快进慢收、无弹跳）。
 */

/** 交互源按压时缩放（按钮/格子按下微缩，松开回弹）。 */
fun Modifier.arkPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "arkPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 交错入场：首次组合时按 index 延迟，淡入 + 上浮。
 * 用于页面区块的“组装感”（明日方舟切页的标志动效）。
 */
@Composable
fun ArkStaggeredIn(
    index: Int,
    modifier: Modifier = Modifier,
    stepMillis: Long = 45L,
    content: @Composable () -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * stepMillis)
        started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(250, easing = EaseOutCubic),
        label = "arkStagger"
    )
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 36f
        }
    ) {
        content()
    }
}

/** 未完成元素的呼吸脉动系数（0.72~1，警示感，周期 1.6s）。 */
@Composable
fun rememberArkPulse(): Float {
    val transition = rememberInfiniteTransition(label = "arkPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arkPulseValue"
    )
    return pulse
}
