package com.cardgame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardgame.core.CardView
import com.cardgame.core.FIELD_SIZE
import com.cardgame.core.GameView
import com.cardgame.core.Phase
import com.cardgame.core.ROUNDS
import com.cardgame.core.Side
import kotlinx.coroutines.delay

private val BattleBg = Brush.verticalGradient(listOf(Pal.bg2, Pal.bg1, Pal.bg0))
private val FieldShape = RoundedCornerShape(18.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun BattleScreen(vm: GameViewModel) {
    val g = vm.view ?: return
    BackHandler { vm.showMenuDialog = true }

    val selected = vm.selectedUid
    val waiting = vm.waitingForPlayer
    val prep = g.phase == Phase.PREP && g.canAct
    val handSelected = selected != null && g.player.hand.any { it.uid == selected }

    // центры карт на экране — для линии удара; обычный HashMap, читается только при отрисовке
    val centers = remember { HashMap<Int, Offset>() }

    // баннер раунда на 1,3 с при смене раунда
    var bannerOn by remember { mutableStateOf(true) }
    LaunchedEffect(g.round) {
        bannerOn = true
        delay(1300)
        bannerOn = false
    }

    Box(Modifier.fillMaxSize().background(BattleBg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            HeroPanel(
                g.enemy,
                if (vm.isOnlineGame) "Соперник · ${g.enemy.faction.title}" else "Враг · ${g.enemy.faction.title}",
                isEnemy = true
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundDiamonds(g.enemy.roundsWon)
                    MenuButton { vm.showMenuDialog = true }
                }
            }

            FieldPanel(vm, g, centers, selected, waiting, prep, handSelected)

            HeroPanel(g.player, playerSubtitle(g), isEnemy = false) {
                RoundDiamonds(g.player.roundsWon, "Раунды")
            }

            InfoStrip(vm, g, selected, waiting, prep)

            Spacer(Modifier.weight(1f))

            HandFan(g.player.hand, selected, dimmed = !prep, onTap = vm::onHandTap)
        }

        StrikeOverlay(vm.fx, centers)
        RoundBanner(bannerOn && g.phase != Phase.GAME_OVER, if (g.isFinal) "ФИНАЛ" else "Раунд ${g.round}")

        // подсказка-тост (например, «эту цель нельзя атаковать»)
        AnimatedVisibility(
            visible = vm.toast != null,
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 70.dp),
            enter = fadeIn(tween(150)) + slideInVertically { -it },
            exit = fadeOut(tween(250)) + slideOutVertically { -it }
        ) {
            Box(
                Modifier.padding(horizontal = 24.dp).clip(PillShape).background(Pal.redDark)
                    .border(1.dp, Pal.red, PillShape).padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(vm.toast ?: "", style = body(13f), textAlign = TextAlign.Center) }
        }

        if (vm.overlayReady && g.phase == Phase.CHOICE) ChoiceDialog(vm, g)
        if (vm.overlayReady && g.phase == Phase.GAME_OVER) GameOverDialog(vm, g)
        if (vm.showMenuDialog) PauseDialog(vm)
        if (vm.showRules) RulesDialog { vm.showRules = false }
    }
}

private fun playerSubtitle(g: GameView): String {
    val round = if (g.isFinal) "ФИНАЛ" else "Раунд ${g.round} из $ROUNDS"
    val state = when (g.phase) {
        Phase.PREP -> if (g.pvp && !g.canAct) "ход соперника" else "ваш ход"
        Phase.BATTLE -> if (g.isFinal) "проход ${g.pass}" else "бой"
        else -> ""
    }
    return if (state.isEmpty()) round else "$round · $state"
}

@Composable
private fun MenuButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressClick(true, onClick).clip(CircleShape).background(Color(0x22FFFFFF))
            .border(1.5.dp, GoldBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("☰", color = Pal.gold, fontSize = 18.sp) }
}

private fun markOf(c: CardView, g: GameView, fx: HitFx?, selected: Int?, waiting: Boolean): CardMark = when {
    fx != null && c.uid == fx.attackerUid -> CardMark.ACTIVE
    fx != null && fx.hits.containsKey(c.uid) -> CardMark.HIT
    fx == null && c.uid == g.attackerUid -> CardMark.ACTIVE
    waiting && c.uid in g.validTargets -> CardMark.TARGET
    c.uid == selected -> CardMark.SELECTED
    else -> CardMark.NONE
}

@Composable
private fun FieldPanel(
    vm: GameViewModel,
    g: GameView,
    centers: HashMap<Int, Offset>,
    selected: Int?,
    waiting: Boolean,
    prep: Boolean,
    handSelected: Boolean
) {
    val fx = vm.fx
    val onCenter = remember(centers) { { uid: Int, o: Offset -> centers[uid] = o } }
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(FieldShape)
            .background(Brush.verticalGradient(listOf(Color(0x1EFFFFFF), Color(0x0AFFFFFF))))
            .border(1.5.dp, Brush.verticalGradient(listOf(Pal.gold.copy(alpha = 0.7f), Pal.goldDark.copy(alpha = 0.4f))), FieldShape)
            .padding(8.dp)
    ) {
        val gap = 6.dp
        val cw = (maxWidth - gap * 4) / 5

        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until FIELD_SIZE) {
                    val c = g.enemy.field.getOrNull(i)
                    if (c != null) key(c.uid) {
                        BattleCard(
                            card = c, width = cw, mark = markOf(c, g, fx, selected, waiting),
                            hit = fx?.hits?.get(c.uid), onCenter = onCenter,
                            onClick = { vm.onEnemyFieldTap(c.uid) }
                        )
                    } else SlotPlaceholder(cw, highlight = false, onClick = null)
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(PillShape).background(Color(0xCC1A1230))
                        .border(1.dp, Pal.gold.copy(alpha = 0.5f), PillShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Crossfade(targetState = vm.centerText, animationSpec = tween(180), label = "center") { t ->
                        Text(t, style = serif(12f, Pal.gold, FontWeight.SemiBold, shadow = false), maxLines = 2)
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    prep -> GoldButton("Закончить ход", enabled = !vm.busy) { vm.onEndTurn() }
                    waiting -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GhostButton("Случайно") { vm.onRandomTarget() }
                        if (!vm.isOnlineGame) GhostButton("Авто") { vm.onAutoBattle() }
                    }
                    else -> Text(
                        if (g.phase == Phase.PREP) "⏳" else "${g.damagePlayer} : ${g.damageEnemy}",
                        color = Pal.textDim, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until FIELD_SIZE) {
                    val c = g.player.field.getOrNull(i)
                    if (c != null) key(c.uid) {
                        BattleCard(
                            card = c, width = cw, mark = markOf(c, g, fx, selected, waiting),
                            hit = fx?.hits?.get(c.uid), onCenter = onCenter,
                            onClick = { vm.onPlayerFieldTap(c.uid) }
                        )
                    } else SlotPlaceholder(
                        cw, highlight = prep && handSelected,
                        onClick = if (prep) ({ vm.onEmptySlotTap() }) else null
                    )
                }
            }
        }
    }
}

/** Что показывать под полем: описание выбранной карты либо подсказку. */
private data class Info(val card: CardView?, val isHand: Boolean, val isOwnField: Boolean, val hint: String)

@Composable
private fun InfoStrip(vm: GameViewModel, g: GameView, selected: Int?, waiting: Boolean, prep: Boolean) {
    val card = (g.player.hand + g.player.field + g.enemy.field).firstOrNull { it.uid == selected }
    val attackerName = (g.player.field + g.enemy.field).firstOrNull { it.uid == g.attackerUid }?.name
    val hint = when {
        g.phase == Phase.PREP && !prep ->
            g.note ?: "Соперник расставляет карты — его поле вы уже видите. Ваша очередь — после него."
        prep -> g.note
            ?: "Выберите карту в руке и выставьте её на поле (максимум $FIELD_SIZE). Лишние карты можно сбросить — в следующем раунде придут новые."
        waiting -> "Ходит $attackerName. Нажмите на вражескую карту, чтобы атаковать."
        g.phase == Phase.BATTLE -> if (vm.isOnlineGame && g.attackerSide == Side.ENEMY) "Ход соперника…" else "Идёт бой…"
        else -> ""
    }
    val info = Info(
        card,
        card != null && g.player.hand.any { it.uid == card.uid },
        card != null && g.player.field.any { it.uid == card.uid },
        hint
    )
    Crossfade(
        targetState = info, animationSpec = tween(160), label = "info",
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 4.dp)
    ) { st ->
        val c = st.card
        if (c != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${c.emoji} ${c.name}", style = serif(14f))
                    Text(
                        "⚔ ${c.atk}   🛡 ${c.def}/${c.maxDef}", style = body(12f, Pal.gold, FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Text(
                        "⚡ ${c.effectTitle}: ${c.effectText}", style = body(11f, Pal.textDim),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (prep && st.isHand) {
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoldButton("На поле", enabled = g.player.field.size < FIELD_SIZE) { vm.playSelected() }
                        GhostButton("Сбросить") { vm.discardSelected() }
                    }
                } else if (prep && st.isOwnField) {
                    GhostButton("Убрать с поля", Modifier.padding(start = 8.dp)) { vm.discardSelected() }
                }
            }
        } else {
            Text(
                st.hint, style = serif(12f, Pal.textDim, FontWeight.Normal, shadow = false),
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
