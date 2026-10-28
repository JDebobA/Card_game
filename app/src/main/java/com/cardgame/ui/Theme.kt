package com.cardgame.ui

import androidx.compose.ui.graphics.Color
import com.cardgame.core.Faction

object Pal {
    val bg0 = Color(0xFF0B0813)
    val bg1 = Color(0xFF1A1230)
    val panel = Color(0xFF1C1530)
    val panel2 = Color(0xFF261D40)
    val gold = Color(0xFFE2B861)
    val goldDark = Color(0xFF8A6A2B)
    val teal = Color(0xFF3FC7A6)
    val tealDark = Color(0xFF123B38)
    val red = Color(0xFFE05A5A)
    val redDark = Color(0xFF3B1620)
    val atk = Color(0xFFF08A3C)
    val def = Color(0xFF4C8DF0)
    val text = Color(0xFFEDE6F7)
    val textDim = Color(0xFF9A90B5)
    val up = Color(0xFFB6FFB0)
    val down = Color(0xFFFFB0B0)
    val line = Color(0x33FFFFFF)
}

/** Верх/низ градиента карты по фракции. */
fun factionGradient(f: Faction): Pair<Color, Color> = when (f) {
    Faction.MONSTERS -> Color(0xFF6B2433) to Color(0xFF2A0D16)
    Faction.MYTHIC -> Color(0xFF23703F) to Color(0xFF0D2A18)
    Faction.HUMANS -> Color(0xFF245A82) to Color(0xFF0D1A2A)
}

fun factionAccent(f: Faction): Color = when (f) {
    Faction.MONSTERS -> Color(0xFFE0607A)
    Faction.MYTHIC -> Color(0xFF5BD68A)
    Faction.HUMANS -> Color(0xFF5BB8E0)
}

fun signed(n: Int): String = if (n >= 0) "+$n" else "−${-n}"
