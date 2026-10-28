package com.cardgame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cardgame.core.BuffOption
import com.cardgame.core.CardLibrary
import com.cardgame.core.CardView
import com.cardgame.core.FIELD_SIZE
import com.cardgame.core.Faction
import com.cardgame.core.GameView
import com.cardgame.core.Mod
import com.cardgame.core.Phase
import com.cardgame.core.ROUNDS
import com.cardgame.core.Side

// ======================================================================= меню

@Composable
fun MenuScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.bg1, Pal.bg0)))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚔️", fontSize = 44.sp)
            Text("Битва колод", color = Pal.gold, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 32.sp)
            Text("Выберите свою колоду", color = Pal.textDim, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, fontSize = 14.sp, modifier = Modifier.padding(bottom = 14.dp))

            for (f in Faction.values()) {
                FactionCard(f, selected = vm.menuFaction == f) { vm.menuFaction = f }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))

            // Локальная игра против бота
            GoldButton("Одиночная игра (Бот)", Modifier.fillMaxWidth()) { vm.startGame() }
            Spacer(Modifier.height(12.dp))

            // Блок сетевой игры
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape)
                    .background(Pal.panel)
                    .border(1.dp, Pal.gold.copy(alpha = 0.4f), shape)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Мультиплеер по сети (ПК)",
                    color = Pal.gold,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = vm.serverIp,
                    onValueChange = { vm.serverIp = it },
                    label = { Text("IP адрес сервера", color = Pal.textDim, fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !vm.isSearchingMatch,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Pal.text,
                        unfocusedTextColor = Pal.text,
                        focusedBorderColor = Pal.gold,
                        unfocusedBorderColor = Pal.line,
                        cursorColor = Pal.gold
                    )
                )

                Spacer(Modifier.height(8.dp))

                GoldButton(
                    text = if (vm.isSearchingMatch) "Отменить поиск" else "Найти игру (1v1)",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (vm.isSearchingMatch) vm.cancelOnlineSearch() else vm.startOnlineSearch()
                }

                if (vm.searchStatusText.isNotEmpty()) {
                    Text(
                        text = vm.searchStatusText,
                        color = Pal.gold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            GhostButton("Правила", Modifier.fillMaxWidth()) { vm.showRules = true }
        }
        if (vm.showRules) RulesDialog { vm.showRules = false }
    }
}

@Composable
private fun FactionCard(f: Faction, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val (top, bottom) = factionGradient(f)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Pal.gold else Pal.line, shape)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.emoji, fontSize = 28.sp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(f.title, color = Pal.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(f.blurb, color = Pal.textDim, fontSize = 12.sp)
            }
            if (selected) Text("✔", color = Pal.gold, fontSize = 20.sp)
        }
        Text(
            CardLibrary.deck(f).joinToString(" · ") { it.name },
            color = Pal.text.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ======================================================================= бой

@Composable
fun BattleScreen(vm: GameViewModel) {
    val g = vm.view ?: return
    BackHandler { vm.showMenuDialog = true }

    val selected = vm.selectedUid
    val fx = vm.fx
    val waiting = vm.waitingForPlayer
    val prep = g.phase == Phase.PREP
    val handSelected = selected != null && g.player.hand.any { it.uid == selected }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.bg1, Pal.bg0)))) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            HeroPanel(g.enemy, if (vm.isOnlineGame) "Соперник · ${g.enemy.faction.title}" else "Враг · ${g.enemy.faction.title}", isEnemy = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundDiamonds(g.enemy.roundsWon)
                    MenuButton { vm.showMenuDialog = true }
                }
            }

            FieldPanel(vm, g, selected, waiting, prep, handSelected)

            HeroPanel(g.player, playerSubtitle(g), isEnemy = false) {
                RoundDiamonds(g.player.roundsWon, "Раунды")
            }

            InfoStrip(vm, g, selected, waiting, prep)

            Spacer(Modifier.weight(1f))

            HandFan(g.player.hand, selected, dimmed = !prep, onTap = { vm.onHandTap(it) })
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
        Phase.PREP -> "ваш ход"
        Phase.BATTLE -> if (g.isFinal) "проход ${g.pass}" else "бой"
        else -> ""
    }
    return if (state.isEmpty()) round else "$round · $state"
}

@Composable
private fun MenuButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0x22FFFFFF))
            .border(1.5.dp, Pal.gold, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text("☰", color = Pal.gold, fontSize = 18.sp) }
}

@Composable
private fun FieldPanel(
    vm: GameViewModel,
    g: GameView,
    selected: Int?,
    waiting: Boolean,
    prep: Boolean,
    handSelected: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val fx = vm.fx
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.5.dp, Pal.gold.copy(alpha = 0.6f), shape)
            .padding(8.dp)
    ) {
        val gap = 6.dp
        val cw = (maxWidth - gap * 4) / 5

        fun borderOf(c: CardView): Pair<Color, androidx.compose.ui.unit.Dp>? = when {
            fx != null && c.uid == fx.attackerUid -> Pal.gold to 3.dp
            fx != null && fx.hits.containsKey(c.uid) -> Pal.red to 3.dp
            fx == null && c.uid == g.attackerUid -> Pal.gold to 3.dp
            waiting && c.uid in g.validTargets -> Pal.red to 3.dp
            c.uid == selected -> Pal.gold to 2.5.dp
            else -> null
        }

        Column {
            // --- враг
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until FIELD_SIZE) {
                    val c = g.enemy.field.getOrNull(i)
                    if (c != null) {
                        val b = borderOf(c)
                        BattleCard(
                            card = c, width = cw,
                            border = b?.first, borderWidth = b?.second ?: 1.5.dp,
                            hit = fx?.hits?.get(c.uid),
                            onClick = { vm.onEnemyFieldTap(c.uid) }
                        )
                    } else SlotPlaceholder(cw, highlight = false, onClick = null)
                }
            }

            // --- центр: подсказка/результат удара и кнопки
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pill = RoundedCornerShape(50)
                Box(
                    Modifier.weight(1f).clip(pill).background(Color(0xCC1A1230))
                        .border(1.dp, Pal.gold.copy(alpha = 0.5f), pill)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(vm.centerText, color = Pal.gold, fontSize = 12.sp, maxLines = 2,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                when {
                    prep -> GoldButton("Закончить ход", enabled = !vm.busy) { vm.onEndTurn() }
                    waiting -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GhostButton("Случайно") { vm.onRandomTarget() }
                        if (!vm.isOnlineGame) {
                            GhostButton("Авто") { vm.onAutoBattle() }
                        }
                    }
                    else -> Text(
                        "${g.damagePlayer} : ${g.damageEnemy}", color = Pal.textDim, fontSize = 12.sp
                    )
                }
            }

            // --- игрок
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until FIELD_SIZE) {
                    val c = g.player.field.getOrNull(i)
                    if (c != null) {
                        val b = borderOf(c)
                        BattleCard(
                            card = c, width = cw,
                            border = b?.first, borderWidth = b?.second ?: 1.5.dp,
                            hit = fx?.hits?.get(c.uid),
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

@Composable
private fun InfoStrip(vm: GameViewModel, g: GameView, selected: Int?, waiting: Boolean, prep: Boolean) {
    val card = (g.player.hand + g.player.field + g.enemy.field).firstOrNull { it.uid == selected }
    val isHand = card != null && g.player.hand.any { it.uid == card.uid }
    val isOwnField = card != null && g.player.field.any { it.uid == card.uid }

    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 4.dp)) {
        if (card != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${card.emoji} ${card.name}   ⚔ ${card.atk}   🛡 ${card.def}/${card.maxDef}",
                        color = Pal.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    Text("⚡ ${card.effectTitle}: ${card.effectText}", color = Pal.textDim, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp))
                }
                if (prep && isHand) {
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoldButton("На поле", enabled = g.player.field.size < FIELD_SIZE) { vm.playSelected() }
                        GhostButton("Сбросить") { vm.discardSelected() }
                    }
                } else if (prep && isOwnField) {
                    GhostButton("Убрать с поля", Modifier.padding(start = 8.dp)) { vm.discardSelected() }
                }
            }
        } else {
            val attackerName = (g.player.field + g.enemy.field).firstOrNull { it.uid == g.attackerUid }?.name
            val hint = when {
                prep -> g.note
                    ?: "Выберите карту в руке и выставьте её на поле (максимум $FIELD_SIZE). Лишние карты можно сбросить — в следующем раунде придут новые."
                waiting -> "Ходит $attackerName. Нажмите на вражескую карту, чтобы атаковать."
                g.phase == Phase.BATTLE -> if (vm.isOnlineGame && g.attackerSide == Side.ENEMY) "Ход соперника…" else "Идёт бой…"
                else -> ""
            }
            Text(hint, color = Pal.textDim, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ======================================================================= диалоги

@Composable
private fun GameDialog(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false
        )
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(shape)
                .background(Pal.panel).border(1.5.dp, Pal.gold, shape)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun ChoiceDialog(vm: GameViewModel, g: GameView) {
    val won = g.roundWinner == Side.PLAYER
    GameDialog {
        Text(
            if (won) "Раунд ${g.round} выигран" else "Раунд ${g.round} проигран",
            color = Pal.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Нанесённый урон — вы ${g.damagePlayer} : ${g.damageEnemy} враг",
            color = Pal.textDim, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
        Text(
            if (won) "Выберите один из трёх вариантов. В каждом — усиление и ослабление и для вас, и для врага."
            else if (vm.isOnlineGame) "Соперник выбирает бафф..." else "Враг выбрал:",
            color = Pal.textDim, fontFamily = FontFamily.Serif, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)
        )
        if (won) {
            g.options.forEachIndexed { i, o ->
                OptionCard(o, selected = vm.chosenIndex == i, enemyChoice = false) { vm.pickOption(i) }
                Spacer(Modifier.height(8.dp))
            }
            Text("Эффекты действуют на все ваши карты — в руке, на поле и в колоде — до конца партии.",
                color = Pal.textDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            GoldButton("Применить и продолжить", Modifier.fillMaxWidth(), enabled = vm.chosenIndex != null) {
                vm.applyChoice()
            }
        } else {
            g.chosen?.let { OptionCard(it, selected = true, enemyChoice = true) {} }
            Spacer(Modifier.height(12.dp))
            GoldButton("Продолжить", Modifier.fillMaxWidth()) { vm.continueAfterEnemyChoice() }
        }
    }
}

@Composable
private fun OptionCard(o: BuffOption, selected: Boolean, enemyChoice: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (enemyChoice) Pal.red else Pal.teal
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Pal.panel2)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Pal.line, shape)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.title, color = Pal.gold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, modifier = Modifier.weight(1f))
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
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.clip(shape).background(color.copy(alpha = 0.10f)).padding(8.dp)) {
        Text(title, color = color, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        mods.forEach {
            Text(it.label(), color = if (it.amount > 0) Pal.up else Pal.down, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun GameOverDialog(vm: GameViewModel, g: GameView) {
    val won = g.winner == Side.PLAYER
    GameDialog {
        Text(if (won) "🏆" else "💀", fontSize = 48.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            if (won) "Победа!" else "Поражение",
            color = if (won) Pal.gold else Pal.red, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (won) "У врага не осталось карт в финальной битве."
            else "В финальной битве у вас не осталось карт.",
            color = Pal.textDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            "Раунды: вы ${g.player.roundsWon} : ${g.enemy.roundsWon} враг",
            color = Pal.text, fontSize = 13.sp, textAlign = TextAlign.Center,
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
private fun PauseDialog(vm: GameViewModel) {
    GameDialog(onDismiss = { vm.showMenuDialog = false }) {
        Text("Пауза", color = Pal.gold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
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
    "Старт" to "Каждый получает 5 карт из своей колоды (10 карт). Выставляйте карты на поле — максимум 5.",
    "Бой" to "Все карты на поле ходят по очереди: первой — карта с наибольшей атакой, затем остальные. Враг бьёт случайную карту, вы выбираете цель сами (или «Случайно» / «Авто»).",
    "Урон" to "Урон равен атаке бьющей карты и отнимается от защиты цели. Защита не восстанавливается — только заменой карты. Карта с защитой 0 уничтожена навсегда.",
    "Раунд" to "Побеждает тот, кто нанёс больше урона. Победитель выбирает один из трёх вариантов: бафф и дебафф себе, бафф и дебафф врагу.",
    "Между раундами" to "Рука добирается до 5. Можно убрать карты с поля (они уходят в сброс), заменить их картами из руки или сбросить карты из руки — тогда в следующем раунде придут новые. Когда колода кончается, сброс возвращается в неё целым и невредимым.",
    "Финал" to "После 3 раундов — финальная битва: карты ходят кругами, пока у одной из сторон не останется ни одной карты на поле.",
    "Эффекты" to "У каждой карты свой уникальный эффект — нажмите на карту, чтобы прочитать его."
)

@Composable
private fun RulesDialog(onClose: () -> Unit) {
    GameDialog(onDismiss = onClose) {
        Text("Правила", color = Pal.gold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        RULES.forEach { (title, body) ->
            Text(title, color = Pal.teal, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, color = Pal.text, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        GoldButton("Понятно", Modifier.fillMaxWidth()) { onClose() }
    }
}