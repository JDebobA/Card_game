package com.cardgame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardgame.core.CardTemplate
import com.cardgame.core.CardView
import com.cardgame.core.Faction

object Pal {
    val bg0 = Color(0xFF07050E)
    val bg1 = Color(0xFF140E27)
    val bg2 = Color(0xFF231A47)
    val panel = Color(0xFF1B1431)
    val panel2 = Color(0xFF2A2048)
    val gold = Color(0xFFF0C766)
    val goldLight = Color(0xFFFFE5A3)
    val goldDark = Color(0xFF8A6A2B)
    val teal = Color(0xFF47E0B9)
    val tealDark = Color(0xFF0F3B36)
    val red = Color(0xFFFF6B6B)
    val redDark = Color(0xFF3B1620)
    val atk = Color(0xFFFF9A3D)
    val def = Color(0xFF4C95FF)
    val text = Color(0xFFF1EBFA)
    val textDim = Color(0xFFA79DC4)
    val up = Color(0xFFA6FFB0)
    val down = Color(0xFFFFA6A6)
    val line = Color(0x33FFFFFF)
}

val GoldBrush: Brush = Brush.verticalGradient(listOf(Color(0xFFFFE9AE), Color(0xFFE2B04A), Color(0xFFB98427)))
val GoldBorder: Brush = Brush.verticalGradient(listOf(Pal.goldLight, Pal.gold))
val GlassBorder: Brush = Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x0AFFFFFF)))

/** Верх/низ градиента карты по фракции. */
fun factionGradient(f: Faction): Pair<Color, Color> = when (f) {
    Faction.MONSTERS -> Color(0xFF7A2A3C) to Color(0xFF260C15)
    Faction.MYTHIC -> Color(0xFF26784A) to Color(0xFF0B2616)
    Faction.HUMANS -> Color(0xFF2A62A0) to Color(0xFF0B1A30)
}

fun factionAccent(f: Faction): Color = when (f) {
    Faction.MONSTERS -> Color(0xFFFF5C7A)
    Faction.MYTHIC -> Color(0xFF5BE38F)
    Faction.HUMANS -> Color(0xFF5CC0FF)
}

fun signed(n: Int): String = if (n >= 0) "+$n" else "−${-n}"

/** Стеклянная панель: полупрозрачная заливка и светлая рамка с градиентом. */
fun Modifier.glass(shape: Shape, fill: Color = Color(0x14FFFFFF)): Modifier =
    this.clip(shape).background(fill).border(1.dp, GlassBorder, shape)

private val TitleShadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 6f)

/** Заголовочный стиль (с засечками и тенью). */
fun serif(size: Float, color: Color = Pal.text, weight: FontWeight = FontWeight.Bold, shadow: Boolean = true) =
    TextStyle(
        color = color, fontSize = size.sp, fontFamily = FontFamily.Serif, fontWeight = weight,
        shadow = if (shadow) TitleShadow else null
    )

fun body(size: Float, color: Color = Pal.text, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight)

/** Карточка колоды для превью в меню. */
fun CardTemplate.toView() = CardView(
    uid = id.hashCode(), name = name, emoji = emoji, faction = faction,
    atk = atk, def = def, maxDef = def, baseAtk = atk, baseDef = def,
    effectTitle = effect.title, effectText = effect.text, alive = true, order = 0
)
