package com.cardgame.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cardgame.core.BuffOption
import com.cardgame.core.FINAL_HAND_SIZE
import com.cardgame.core.GameView
import com.cardgame.core.HAND_SIZE
import com.cardgame.core.Mod
import com.cardgame.core.ROUNDS
import com.cardgame.core.Side

private val DialogShape = RoundedCornerShape(22.dp)
private val DialogFill = Brush.verticalGradient(listOf(Color(0xFF2A2050), Color(0xFF150F2B)))

/** Окно с плавным «выскакиванием». */
@Composable
fun GameDialog(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false
        )
    ) {
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f)) }
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.85f + 0.15f * p.value
                    scaleX = s; scaleY = s
                    alpha = p.value.coerceIn(0f, 1f)
                }
                .clip(DialogShape)
                .background(DialogFill)
                .border(1.5.dp, GoldBorder, DialogShape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            content = content
        )
    }
}

/** Баннер «Раунд N» / «ФИНАЛ» по центру поля. */
@Composable
fun RoundBanner(visible: Boolean, text: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.5f, animationSpec = spring(Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.3f, animationSpec = tween(300))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(Color(0xE6140E27))
                    .border(1.5.dp, GoldBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 36.dp, vertical = 16.dp)
            ) { Text(text, style = serif(30f, Pal.gold)) }
        }
    }
}

@Composable
fun ChoiceDialog(vm: GameViewModel, g: GameView) {
    val won = g.roundWinner == Side.PLAYER
    GameDialog {
        Text(
            if (won) "Раунд ${g.round} выигран" else "Раунд ${g.round} проигран",
            style = serif(26f, if (won) Pal.gold else Pal.text), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Нанесённый урон — вы ${g.damagePlayer} : ${g.damageEnemy} враг",
            style = body(12f, Pal.textDim), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
        Text(
            when {
                won -> "Выберите один из трёх вариантов: усиление и ослабление и для вас, и для врага."
                vm.isOnlineGame -> "Соперник выбирает бафф…"
                else -> "Враг выбрал:"
            },
            style = serif(13f, Pal.textDim, FontWeight.Normal, shadow = false), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
        )
        if (won) {
            g.options.forEachIndexed { i, o ->
                Appear(i) { OptionCard(o, vm.chosenIndex == i, false) { vm.pickOption(i) } }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Эффекты действуют на все ваши карты — в руке, на поле и в колоде — до конца партии.",
                style = body(11f, Pal.textDim), modifier = Modifier.padding(vertical = 6.dp)
            )
            GoldButton("Применить и продолжить", Modifier.fillMaxWidth(), enabled = vm.chosenIndex != null) {
                vm.applyChoice()
            }
        } else {
            g.chosen?.let { OptionCard(it, selected = true, enemyChoice = true) {} }
            Spacer(Modifier.height(12.dp))
            if (vm.isOnlineGame) {
                Text(
                    "Ожидание выбора соперника…", style = body(13f, Pal.gold), fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            } else {
                GoldButton("Продолжить", Modifier.fillMaxWidth()) { vm.continueAfterEnemyChoice() }
            }
        }
    }
}

@Composable
private fun OptionCard(o: BuffOption, selected: Boolean, enemyChoice: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (enemyChoice) Pal.red else Pal.teal
    Column(
        Modifier.fillMaxWidth()
            .pressClick(true, onClick)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Pal.panel2)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Pal.line, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.title, style = serif(15f, Pal.gold), modifier = Modifier.weight(1f))
            if (selected && !enemyChoice) Text("✔", color = Pal.teal, fontSize = 16.sp)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModBlock(if (enemyChoice) "Врагу" else "Вам", Pal.teal, listOf(o.selfBuff, o.selfDebuff), Modifier.weight(1f))
            ModBlock(if (enemyChoice) "Вам" else "Врагу", Pal.red, listOf(o.enemyBuff, o.enemyDebuff), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModBlock(title: String, color: Color, mods: List<Mod>, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)).padding(8.dp)) {
        Text(title, style = serif(12f, color, shadow = false))
        mods.forEach {
            Text(
                it.label(), color = if (it.amount > 0) Pal.up else Pal.down, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun GameOverDialog(vm: GameViewModel, g: GameView) {
    val won = g.winner == Side.PLAYER
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(Unit) { bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 200f)) }
    GameDialog {
        Text(
            if (won) "🏆" else "💀", fontSize = 56.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
        )
        Text(
            if (won) "Победа!" else "Поражение",
            style = serif(32f, if (won) Pal.gold else Pal.red), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (won) "У соперника не осталось карт в финальной битве."
            else "В финальной битве у вас не осталось карт.",
            style = body(13f, Pal.textDim), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            "Раунды: вы ${g.player.roundsWon} : ${g.enemy.roundsWon} враг",
            style = body(13f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        )
        if (!vm.isOnlineGame) {
            GoldButton("Сыграть снова", Modifier.fillMaxWidth()) { vm.startGame() }
            Spacer(Modifier.height(8.dp))
        }
        GhostButton("В главное меню", Modifier.fillMaxWidth()) { vm.toMenu() }
    }
}

@Composable
fun PauseDialog(vm: GameViewModel) {
    GameDialog(onDismiss = { vm.showMenuDialog = false }) {
        Text(
            "Пауза", style = serif(26f, Pal.gold), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        GoldButton("Продолжить", Modifier.fillMaxWidth()) { vm.showMenuDialog = false }
        Spacer(Modifier.height(8.dp))
        GhostButton("Правила", Modifier.fillMaxWidth()) { vm.showRules = true }
        if (!vm.isOnlineGame) {
            Spacer(Modifier.height(8.dp))
            GhostButton("Новая игра", Modifier.fillMaxWidth()) { vm.startGame() }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("В главное меню", Modifier.fillMaxWidth()) { vm.toMenu() }
    }
}

private val RULES = listOf(
    "Старт" to "Каждый получает $HAND_SIZE карт из своей колоды (10 карт). Выставляйте карты на поле — максимум 5.",
    "Бой" to "Карты на поле ходят по очереди: первой — с наибольшей атакой. Враг бьёт случайную карту, вы выбираете цель сами (или «Случайно» / «Авто»).",
    "Урон" to "Урон равен атаке бьющей карты и отнимается от защиты цели. Защита не восстанавливается — только заменой карты. Карта с защитой 0 выбывает.",
    "Раунды" to "Всего $ROUNDS раундов. Побеждает тот, кто нанёс больше урона, и выбирает один из трёх вариантов: бафф и дебафф себе, бафф и дебафф врагу.",
    "Между раундами" to "Рука добирается до $HAND_SIZE (если там уже $HAND_SIZE или больше — ничего не выдаётся). Можно убрать карты с поля, заменить их картами из руки или сбросить карты из руки. Когда колода кончается, сброс возвращается в неё целым.",
    "Финал" to "После $ROUNDS раундов — финальная битва: рука добирается только до $FINAL_HAND_SIZE карт, затем карты ходят кругами, пока у одной стороны не останется ни одной карты на поле.",
    "Эффекты" to "У каждой карты свой уникальный эффект — нажмите на карту, чтобы прочитать его."
)

@Composable
fun RulesDialog(onClose: () -> Unit) {
    GameDialog(onDismiss = onClose) {
        Text(
            "Правила", style = serif(26f, Pal.gold), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
        RULES.forEach { (title, text) ->
            Text(title, style = serif(14f, Pal.teal, shadow = false))
            Text(text, style = body(13f), modifier = Modifier.padding(bottom = 8.dp))
        }
        GoldButton("Понятно", Modifier.fillMaxWidth()) { onClose() }
    }
}
