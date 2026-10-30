package com.cardgame.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Пульсирующая рамка. Бесконечная анимация существует, только пока рамка на экране. */
@Composable
fun PulseFrame(color: Color, cornerDp: Dp, width: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a = t.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Box(
        modifier.drawBehind {
            drawRoundRect(
                color = color.copy(alpha = a.value),
                style = Stroke(width = width.toPx()),
                cornerRadius = CornerRadius(cornerDp.toPx())
            )
        }
    )
}

/** Плавное появление содержимого снизу с задержкой по номеру. */
@Composable
fun Appear(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        p.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Box(
        modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 24.dp.toPx()
        }
    ) { content() }
}

/**
 * Эффект удара: светящийся «снаряд» летит от атакующей карты к цели, на цели расходится кольцо.
 * centers — центры карт в координатах окна (заполняются самими картами).
 */
@Composable
fun StrikeOverlay(fx: HitFx?, centers: Map<Int, Offset>) {
    val flight = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(fx) {
        flight.snapTo(0f)
        ring.snapTo(0f)
        if (fx != null) {
            flight.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            ring.animateTo(1f, tween(380))
        }
    }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val f = fx ?: return@Canvas
        val from = (centers[f.attackerUid] ?: return@Canvas) - origin
        val to = (centers[f.targetUid] ?: return@Canvas) - origin
        val p = flight.value
        if (p > 0f && p < 1f) {
            val head = from + (to - from) * p
            val tail = from + (to - from) * (p - 0.35f).coerceAtLeast(0f)
            drawLine(
                brush = Brush.linearGradient(listOf(Color.Transparent, Pal.goldLight), start = tail, end = head),
                start = tail, end = head, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round
            )
            drawCircle(Pal.goldLight, radius = 7.dp.toPx(), center = head)
            drawCircle(
                brush = Brush.radialGradient(listOf(Pal.gold.copy(alpha = 0.6f), Color.Transparent), center = head, radius = 26.dp.toPx()),
                radius = 26.dp.toPx(), center = head
            )
        }
        val r = ring.value
        if (r > 0f && r < 1f) {
            drawCircle(
                color = Pal.red.copy(alpha = 0.9f * (1f - r)), radius = (12 + 38 * r).dp.toPx(), center = to,
                style = Stroke(width = (5f * (1f - r) + 1f).dp.toPx())
            )
        }
    }
}

/**
 * Фон главного меню. Два больших свечения нарисованы ОДИН раз (drawWithCache) и дальше только
 * двигаются слоем GPU (graphicsLayer) — без перерисовки. Звёзды мерцают в отдельном маленьком Canvas.
 */
@Composable
fun MenuBackdrop() {
    val t = rememberInfiniteTransition(label = "backdrop")
    val phase = t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "phase"
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val p = phase.value * 2f * PI.toFloat()
                    translationX = size.width * 0.12f * sin(p)
                    translationY = size.height * 0.03f * sin(p * 2f)
                }
                .drawWithCache {
                    val c = Offset(size.width * 0.25f, size.height * 0.16f)
                    val r = size.width * 0.75f
                    val brush = Brush.radialGradient(listOf(Color(0x558B5CF6), Color.Transparent), center = c, radius = r)
                    onDrawBehind { drawCircle(brush, radius = r, center = c) }
                }
        )
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val p = phase.value * 2f * PI.toFloat()
                    translationX = -size.width * 0.1f * sin(p)
                    translationY = size.height * 0.04f * sin(p)
                }
                .drawWithCache {
                    val c = Offset(size.width * 0.8f, size.height * 0.62f)
                    val r = size.width * 0.7f
                    val brush = Brush.radialGradient(listOf(Color(0x40F0C766), Color.Transparent), center = c, radius = r)
                    onDrawBehind { drawCircle(brush, radius = r, center = c) }
                }
        )
        Stars(phase)
    }
}

@Composable
private fun Stars(phase: androidx.compose.runtime.State<Float>) {
    val stars = remember {
        val r = kotlin.random.Random(7)
        List(24) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.4f, r.nextFloat()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = phase.value * 2f * PI.toFloat()
        for (s in stars) {
            val tw = 0.3f + 0.7f * abs(sin(p + s[3] * 6.28f))
            drawCircle(Color.White.copy(alpha = 0.6f * tw), radius = s[2], center = Offset(s[0] * size.width, s[1] * size.height))
        }
    }
}
