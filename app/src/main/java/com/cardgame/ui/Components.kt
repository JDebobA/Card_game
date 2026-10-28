package com.cardgame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cardgame.core.BoardView
import com.cardgame.core.CardView
import kotlin.math.abs

// ---------------------------------------------------------------- кнопки / чипы

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFF1D58A), Color(0xFFC79A3F))))
            .border(1.dp, Pal.goldDark, shape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = Color(0xFF2A1D05), fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif, fontSize = 14.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Color(0x22FFFFFF))
            .border(1.dp, Pal.gold.copy(alpha = 0.6f), shape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Pal.gold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, color: Color = Pal.textDim) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Color(0x22FFFFFF))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- карта

@Composable
private fun StatPill(label: String, value: Int, color: Color, tint: Color, u: Float) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = (5 * u).dp, vertical = (1 * u).dp)
    ) {
        Text("$label$value", color = tint, fontSize = (10 * u).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Карта. width = 60.dp — карта на поле, ~84.dp — карта в руке.
 * hit — «−N» поверх карты (анимация удара).
 */
@Composable
fun BattleCard(
    card: CardView,
    modifier: Modifier = Modifier,
    width: Dp = 60.dp,
    border: Color? = null,
    borderWidth: Dp = 1.5.dp,
    hit: Int? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val u = width.value / 60f
    val height = width * 1.4f
    val shape = RoundedCornerShape((8 * u).dp)
    val (top, bottom) = factionGradient(card.faction)
    val edge = border ?: factionAccent(card.faction).copy(alpha = 0.7f)
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
    Box(
        modifier
            .size(width, height)
            .alpha(if (!card.alive) 0.35f else if (dimmed) 0.6f else 1f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(borderWidth, edge, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Text(
            card.emoji, fontSize = (26 * u).sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = (9 * u).dp)
        )
        // значок эффекта
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding((3 * u).dp)
                .size((13 * u).dp)
                .clip(CircleShape)
                .background(Color(0xAA000000)),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = (8 * u).sp, color = Pal.gold) }
        // порядок хода
        if (card.order > 0 && card.alive) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding((3 * u).dp)
                    .size((14 * u).dp)
                    .clip(CircleShape)
                    .background(Pal.gold),
                contentAlignment = Alignment.Center
            ) { Text("${card.order}", fontSize = (8 * u).sp, color = Color(0xFF2A1D05), fontWeight = FontWeight.Bold) }
        }
        Text(
            card.name,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = (2 * u).dp, end = (2 * u).dp, bottom = (22 * u).dp)
                .fillMaxWidth(),
            color = Pal.text, fontSize = (8.5f * u).sp, lineHeight = (9.5f * u).sp,
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = (3 * u).dp, vertical = (3 * u).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatPill("⚔", card.atk, Pal.atk, atkTint, u)
            StatPill("🛡", card.def, Pal.def, defTint, u)
        }
        if (!card.alive) {
            Text("☠", fontSize = (28 * u).sp, modifier = Modifier.align(Alignment.Center))
        }
        if (hit != null) {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text("−$hit", color = Pal.red, fontSize = (16 * u).sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
fun SlotPlaceholder(width: Dp, highlight: Boolean, onClick: (() -> Unit)?) {
    val color = if (highlight) Pal.teal else Color(0x55FFFFFF)
    Box(
        Modifier
            .size(width, width * 1.4f)
            .drawBehind {
                drawRoundRect(
                    color = color,
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    cornerRadius = CornerRadius(20f)
                )
            }
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text("+", color = color, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------- рука «веером»

@Composable
fun HandFan(
    hand: List<CardView>,
    selectedUid: Int?,
    dimmed: Boolean,
    onTap: (Int) -> Unit
) {
    val cardW = 84.dp
    val step = 62.dp
    val n = hand.size
    Box(Modifier.fillMaxWidth().height(158.dp), contentAlignment = Alignment.BottomCenter) {
        if (n == 0) {
            Text("Рука пуста — новые карты придут в следующем раунде", color = Pal.textDim, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 40.dp))
        } else {
            Box(Modifier.width(cardW + step * (n - 1)).height(150.dp)) {
                val mid = (n - 1) / 2f
                hand.forEachIndexed { i, c ->
                    val sel = c.uid == selectedUid
                    val arc = abs(i - mid) * 5f
                    BattleCard(
                        card = c,
                        width = cardW,
                        border = if (sel) Pal.gold else null,
                        borderWidth = if (sel) 2.5.dp else 1.5.dp,
                        dimmed = dimmed && !sel,
                        modifier = Modifier
                            .offset(x = step * i, y = if (sel) 0.dp else (24f + arc).dp)
                            .zIndex(if (sel) 20f else i.toFloat())
                            .graphicsLayer(rotationZ = if (sel) 0f else (i - mid) * 4f),
                        onClick = { onTap(c.uid) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- панель героя

@Composable
fun HeroPanel(
    board: BoardView,
    subtitle: String,
    isEnemy: Boolean,
    trailing: @Composable () -> Unit
) {
    val accent = if (isEnemy) Pal.red else Pal.teal
    val (top, bottom) = factionGradient(board.faction)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(top, bottom)))
                    .border(2.dp, Pal.gold, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(board.faction.emoji, fontSize = 22.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(board.heroName, color = Pal.text, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(subtitle, color = Pal.textDim, fontSize = 11.sp, maxLines = 1,
                    fontFamily = FontFamily.Serif)
                Spacer4()
                val frac = if (board.totalMaxDef == 0) 0f
                else (board.totalDef.toFloat() / board.totalMaxDef).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent)))
                    )
                    Text("${board.totalDef} / ${board.totalMaxDef}", color = Color.White, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
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

@Composable
private fun Spacer4() {
    Box(Modifier.height(4.dp))
}

@Composable
fun RoundDiamonds(won: Int, label: String? = null) {
    Column(horizontalAlignment = Alignment.End) {
        if (label != null) Text(label, color = Pal.textDim, fontSize = 10.sp, fontFamily = FontFamily.Serif)
        Row {
            repeat(3) { i ->
                Text(if (i < won) "◆" else "◇", color = Pal.gold, fontSize = 15.sp)
            }
        }
    }
}
