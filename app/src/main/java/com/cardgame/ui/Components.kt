package com.cardgame.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cardgame.core.BoardView
import com.cardgame.core.CardView
import com.cardgame.core.ROUNDS
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- нажатие с «пружинкой»

/** Кнопка/плитка, которая слегка сжимается под пальцем. Без ripple — вместо него масштаб. */
@Composable
fun Modifier.pressClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

// ---------------------------------------------------------------- кнопки / чипы

private val BtnShape = RoundedCornerShape(16.dp)
private val GhostShape = RoundedCornerShape(14.dp)
private val GoldFill = Brush.verticalGradient(listOf(Color(0xFFFFE9AE), Color(0xFFE5B64F), Color(0xFFB98427)))
private val GoldStroke = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0xFFFFF3CC), Color(0xFF8A6A2B))))

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressClick(enabled, onClick)
            .clip(BtnShape)
            .background(GoldFill)
            .border(GoldStroke, BtnShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = Color(0xFF2A1D05), fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Serif, fontSize = 15.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressClick(enabled, onClick)
            .glass(GhostShape, Color(0x1AFFFFFF))
            .border(1.dp, Pal.gold.copy(alpha = 0.55f), GhostShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Pal.gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, color: Color = Pal.textDim) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- карта

enum class CardMark { NONE, SELECTED, ACTIVE, TARGET, HIT }

@Composable
private fun StatBadge(icon: String, value: Int, color: Color, tint: Color, size: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.65f))))
            .border(1.5.dp, Color(0xFF120B22), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = (size.value * 0.3f).sp, lineHeight = (size.value * 0.32f).sp)
            Text(
                "$value", color = tint, fontSize = (size.value * 0.46f).sp, lineHeight = (size.value * 0.46f).sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1
            )
        }
    }
}

/**
 * Карта. width = 60.dp — на поле, ~84.dp — в руке.
 * hit — «−N» над картой при ударе; onCenter сообщает центр карты (для линии удара).
 */
@Composable
fun BattleCard(
    card: CardView,
    modifier: Modifier = Modifier,
    width: Dp = 60.dp,
    mark: CardMark = CardMark.NONE,
    hit: Int? = null,
    dimmed: Boolean = false,
    onCenter: ((Int, Offset) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val u = width.value / 60f
    val height = width * 1.42f
    val shape = remember(width) { RoundedCornerShape((9 * u).dp) }
    val (top, bottom) = remember(card.faction) { factionGradient(card.faction) }
    val accent = remember(card.faction) { factionAccent(card.faction) }
    val density = LocalDensity.current.density

    // Появление карты: «вылетает» с пружинкой.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) }

    // Тряска и «−N» при ударе.
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(hit) {
        if (hit != null) {
            pop.snapTo(0f)
            val amp = 7f * density
            coroutineScope {
                launch { pop.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
                for (s in floatArrayOf(amp, -amp, amp * 0.6f, -amp * 0.6f, 0f)) shake.animateTo(s, tween(50))
            }
        } else {
            pop.snapTo(0f)
        }
    }

    val edge by animateColorAsState(
        when (mark) {
            CardMark.SELECTED, CardMark.ACTIVE -> Pal.gold
            CardMark.TARGET, CardMark.HIT -> Pal.red
            CardMark.NONE -> accent.copy(alpha = 0.7f)
        }, tween(160), label = "edge"
    )
    val edgeWidth = if (mark == CardMark.NONE) 1.5.dp else 2.5.dp

    val atkTint = when {
        card.atk > card.baseAtk -> Pal.up
        card.atk < card.baseAtk -> Pal.down
        else -> Color.White
    }
    val defTint = when {
        card.def < card.maxDef -> Pal.down
        card.maxDef > card.baseDef -> Pal.up
        card.maxDef < card.baseDef -> Pal.down
        else -> Color.White
    }
    val hpFrac by animateFloatAsState(
        if (card.maxDef <= 0) 0f else (card.def.toFloat() / card.maxDef).coerceIn(0f, 1f),
        tween(350), label = "hp"
    )
    val deadAlpha by animateFloatAsState(if (card.alive) 1f else 0.35f, tween(400), label = "dead")

    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                val s = 0.4f + 0.6f * appear.value
                scaleX = s; scaleY = s
                alpha = (appear.value.coerceIn(0f, 1f)) * deadAlpha * (if (dimmed) 0.65f else 1f)
                translationX = shake.value
            }
            .then(
                if (onCenter != null) Modifier.onGloballyPositioned {
                    onCenter(card.uid, it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                } else Modifier
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth().fillMaxHeight()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .border(edgeWidth, edge, shape)
                .then(if (onClick != null) Modifier.pressClick(true, onClick) else Modifier)
        ) {
            // свечение под эмодзи
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = (4 * u).dp).size((40 * u).dp)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)), CircleShape)
            )
            Text(
                card.emoji, fontSize = (26 * u).sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = (9 * u).dp)
            )
            if (card.order > 0 && card.alive) {
                Box(
                    Modifier.align(Alignment.TopStart).padding((3 * u).dp).size((15 * u).dp)
                        .clip(CircleShape).background(GoldBrush),
                    contentAlignment = Alignment.Center
                ) { Text("${card.order}", fontSize = (9 * u).sp, color = Color(0xFF2A1D05), fontWeight = FontWeight.ExtraBold) }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding((3 * u).dp).size((13 * u).dp)
                    .clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) { Text("⚡", fontSize = (8 * u).sp, color = Pal.gold) }

            Text(
                card.name,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = (3 * u).dp, end = (3 * u).dp, bottom = (24 * u).dp),
                color = Pal.text, fontSize = (8.5f * u).sp, lineHeight = (9.5f * u).sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            // полоска оставшейся защиты
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = (6 * u).dp).padding(bottom = (5 * u).dp)
                    .height((4 * u).dp).clip(CircleShape).background(Color(0x66000000))
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(hpFrac)
                        .background(if (hpFrac > 0.5f) Pal.teal else if (hpFrac > 0.25f) Pal.atk else Pal.red)
                )
            }
            if (!card.alive) Text("☠", fontSize = (28 * u).sp, modifier = Modifier.align(Alignment.Center))
        }
        // значки ⚔ / 🛡 выступают за край карты
        val badge = (24 * u).dp
        StatBadge("⚔", card.atk, Pal.atk, atkTint, badge, Modifier.align(Alignment.BottomStart).offset((-3 * u).dp, (5 * u).dp))
        StatBadge("🛡", card.def, Pal.def, defTint, badge, Modifier.align(Alignment.BottomEnd).offset((3 * u).dp, (5 * u).dp))

        if (hit != null) {
            Box(
                Modifier.align(Alignment.Center)
                    .graphicsLayer {
                        val p = pop.value
                        translationY = -26.dp.toPx() * p
                        alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
                        val sc = if (p < 0.2f) 0.6f + 2f * p else 1f
                        scaleX = sc; scaleY = sc
                    }
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xDD000000))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text("−$hit", color = Pal.red, fontSize = (16 * u).sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
fun SlotPlaceholder(width: Dp, highlight: Boolean, onClick: (() -> Unit)?) {
    val color by animateColorAsState(if (highlight) Pal.teal else Color(0x44FFFFFF), tween(200), label = "slot")
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) }
    Box(
        Modifier
            .size(width, width * 1.42f)
            .drawBehind {
                drawRoundRect(
                    color = color, style = Stroke(width = 3f, pathEffect = dash),
                    cornerRadius = CornerRadius(22f)
                )
            }
            .clip(RoundedCornerShape(9.dp))
            .then(if (onClick != null) Modifier.pressClick(true, onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text("+", color = color, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------- рука «веером»

@Composable
private fun HandCard(
    card: CardView, i: Int, n: Int, step: Dp, cardW: Dp, selected: Boolean, dimmed: Boolean, onTap: (Int) -> Unit
) {
    val mid = (n - 1) / 2f
    val y by animateDpAsState(
        if (selected) (-6).dp else (22f + abs(i - mid) * 5f).dp,
        spring(dampingRatio = 0.7f, stiffness = 500f), label = "handY"
    )
    val rot by animateFloatAsState(
        if (selected) 0f else (i - mid) * 4f, spring(stiffness = 500f), label = "handRot"
    )
    val x by animateDpAsState(step * i, spring(stiffness = 300f), label = "handX")
    BattleCard(
        card = card, width = cardW,
        mark = if (selected) CardMark.SELECTED else CardMark.NONE,
        dimmed = dimmed && !selected,
        modifier = Modifier
            .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
            .zIndex(if (selected) 20f else i.toFloat())
            .graphicsLayer { rotationZ = rot },
        onClick = { onTap(card.uid) }
    )
}

@Composable
fun HandFan(hand: List<CardView>, selectedUid: Int?, dimmed: Boolean, onTap: (Int) -> Unit) {
    val cardW = 84.dp
    val n = hand.size
    BoxWithConstraints(Modifier.fillMaxWidth().height(158.dp), contentAlignment = Alignment.BottomCenter) {
        if (n == 0) {
            Text(
                "Рука пуста", color = Pal.textDim, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 40.dp)
            )
        } else {
            val step = if (n == 1) 0.dp else minOf(64.dp, (maxWidth - cardW - 8.dp) / (n - 1))
            Box(Modifier.width(cardW + step * (n - 1)).height(150.dp)) {
                hand.forEachIndexed { i, c ->
                    androidx.compose.runtime.key(c.uid) {
                        HandCard(c, i, n, step, cardW, c.uid == selectedUid, dimmed, onTap)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- панель героя

@Composable
fun HeroPanel(board: BoardView, subtitle: String, isEnemy: Boolean, trailing: @Composable () -> Unit) {
    val accent = if (isEnemy) Pal.red else Pal.teal
    val (top, bottom) = remember(board.faction) { factionGradient(board.faction) }
    val frac by animateFloatAsState(
        if (board.totalMaxDef == 0) 0f else (board.totalDef.toFloat() / board.totalMaxDef).coerceIn(0f, 1f),
        tween(450), label = "heroHp"
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(top, bottom)))
                    .border(2.dp, GoldBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(board.faction.emoji, fontSize = 22.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(board.heroName, style = serif(16f), maxLines = 1)
                Text(subtitle, style = serif(11f, Pal.textDim, FontWeight.Normal, shadow = false), maxLines = 1)
                Box(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent)))
                    )
                    Text(
                        "${board.totalDef} / ${board.totalMaxDef}", color = Color.White, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            trailing()
        }
        Row(Modifier.padding(top = 5.dp, start = 58.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip("⚔ Сила ${board.totalAtk}")
            Chip("Рука ${board.handCount} · Колода ${board.drawCount}")
            val parts = buildList {
                if (board.atkMod != 0) add("${signed(board.atkMod)}⚔")
                if (board.defMod != 0) add("${signed(board.defMod)}🛡")
            }
            if (parts.isNotEmpty()) {
                val net = board.atkMod + board.defMod
                Chip(parts.joinToString(" "), if (net >= 0) Pal.teal else Pal.red)
            }
        }
    }
}

/** Победы в раундах: ромбы по числу раундов, выигранный «вспыхивает». */
@Composable
fun RoundDiamonds(won: Int, label: String? = null) {
    Column(horizontalAlignment = Alignment.End) {
        if (label != null) Text(label, style = serif(10f, Pal.textDim, FontWeight.Normal, shadow = false))
        Row {
            repeat(ROUNDS) { i ->
                val on = i < won
                val s by animateFloatAsState(if (on) 1f else 0.85f, spring(Spring.DampingRatioHighBouncy), label = "dia")
                Text(
                    if (on) "◆" else "◇", color = if (on) Pal.gold else Pal.goldDark, fontSize = 14.sp,
                    modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }
                )
            }
        }
    }
}
