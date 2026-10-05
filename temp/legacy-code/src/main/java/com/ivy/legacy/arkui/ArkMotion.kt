package com.ivy.legacy.arkui

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * 扫光：一条 ~20° 斜高光带周期性扫过（明日方舟活动横幅标志动效）。
 * 叠加在卡片内容之上；默认 4.8s 一次，扫出右边界后从头再来。
 */
fun Modifier.arkSweepShine(
    periodMillis: Int = 4800,
    shineColor: Color = Color.White.copy(alpha = 0.16f),
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "arkShine")
    val x by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
        ),
        label = "arkShineX"
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.22f
        val start = x * (size.width + band) - band
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, shineColor, Color.Transparent),
                start = Offset(start, -size.height * 0.2f),
                end = Offset(start + band, size.height * 1.2f),
            )
        )
    }
}

/**
 * HUD 边角括号（战术取景框，A7）：四角细线 L 形，框住卡片/大图。
 * 画在内容之下（drawBehind），不遮挡触摸。
 */
fun Modifier.arkCornerBrackets(
    color: Color,
    strokeWidthDp: Dp = 3.dp,
    armLengthDp: Dp = 28.dp,
    insetDp: Dp = 0.dp,
): Modifier = composed {
    val stroke = with(LocalDensity.current) { strokeWidthDp.toPx() }
    val arm = with(LocalDensity.current) { armLengthDp.toPx() }
    val inset = with(LocalDensity.current) { insetDp.toPx() }
    drawBehind {
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        val x0 = inset
        val y0 = inset
        val x1 = inset + w
        val y1 = inset + h
        // 左上
        drawLine(color, Offset(x0, y0 + arm), Offset(x0, y0), strokeWidth = stroke)
        drawLine(color, Offset(x0, y0), Offset(x0 + arm, y0), strokeWidth = stroke)
        // 右上
        drawLine(color, Offset(x1 - arm, y0), Offset(x1, y0), strokeWidth = stroke)
        drawLine(color, Offset(x1, y0), Offset(x1, y0 + arm), strokeWidth = stroke)
        // 左下
        drawLine(color, Offset(x0, y1 - arm), Offset(x0, y1), strokeWidth = stroke)
        drawLine(color, Offset(x0, y1), Offset(x0 + arm, y1), strokeWidth = stroke)
        // 右下
        drawLine(color, Offset(x1 - arm, y1), Offset(x1, y1), strokeWidth = stroke)
        drawLine(color, Offset(x1, y1), Offset(x1, y1 - arm), strokeWidth = stroke)
    }
}

/**
 * 数值滚动：target 变化（或首次出现）时从 0 滚到 target，快进慢收。
 * 返回当前应显示的值——配合金额 format 使用（Novecento 数字字体下最出"作战数值"感）。
 */
@Composable
fun rememberArkCountUp(target: Double, durationMillis: Int = 500): Double {
    var value by remember { mutableStateOf(0.0) }
    LaunchedEffect(target) {
        value = 0.0
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = EaseOutCubic),
        ) { progress, _ ->
            value = target * progress
        }
    }
    return value
}
