package com.cardgame.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardgame.core.CardLibrary
import com.cardgame.core.CardTemplate
import com.cardgame.core.Faction
import com.cardgame.core.ROUNDS
import com.cardgame.network.DiscoveredHost

private val MenuBg = Brush.verticalGradient(listOf(Pal.bg2, Pal.bg1, Pal.bg0))
private val TileShape = RoundedCornerShape(18.dp)

@Composable
fun MenuScreen(vm: GameViewModel) {
    DisposableEffect(Unit) {
        vm.startMenuProbe()
        onDispose { vm.stopMenuProbe() }
    }
    Box(Modifier.fillMaxSize().background(MenuBg)) {
        MenuBackdrop()
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            vm.menuMessage?.let { msg ->
                MessageBanner(msg) { vm.menuMessage = null }
                Spacer(Modifier.height(10.dp))
            }

            Appear(0) { Logo() }
            Spacer(Modifier.height(16.dp))

            Appear(1) {
                Column(Modifier.fillMaxWidth()) {
                    SectionLabel("Выберите колоду")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in Faction.values()) {
                            FactionTile(f, vm.menuFaction == f, Modifier.weight(1f)) { vm.menuFaction = f }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Appear(2) { DeckPreview(vm.menuFaction) }
            Spacer(Modifier.height(16.dp))

            Appear(3) {
                GoldButton("⚔  Играть против бота", Modifier.fillMaxWidth()) { vm.startGame() }
            }
            Spacer(Modifier.height(14.dp))

            Appear(4) { OnlineBlock(vm) }
            Spacer(Modifier.height(10.dp))
            Appear(5) { LanBlock(vm) }
            Spacer(Modifier.height(12.dp))
            GhostButton("Правила игры", Modifier.fillMaxWidth()) { vm.showRules = true }
            Spacer(Modifier.height(8.dp))
        }
        when (vm.lanDialog) {
            LanDialog.HOST -> LanHostDialog(vm)
            LanDialog.JOIN -> LanJoinDialog(vm)
            LanDialog.NONE -> Unit
        }
        if (vm.showRules) RulesDialog { vm.showRules = false }
    }
}

@Composable
private fun Logo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚔️", fontSize = 40.sp)
        Text("БИТВА КОЛОД", style = serif(34f).copy(brush = GoldBrush, letterSpacing = 2.sp))
        Text(
            "$ROUNDS раундов · финальная битва",
            style = serif(13f, Pal.textDim, FontWeight.Normal, shadow = false), fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = body(11f, Pal.gold, FontWeight.Bold).copy(letterSpacing = 1.5.sp),
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun MessageBanner(text: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Pal.redDark)
            .border(1.dp, Pal.red, shape).clickable { onDismiss() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = body(13f), modifier = Modifier.weight(1f))
        Text("✕", color = Pal.down, fontSize = 14.sp)
    }
}

@Composable
private fun FactionTile(f: Faction, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (top, bottom) = remember(f) { factionGradient(f) }
    val accent = remember(f) { factionAccent(f) }
    val border by animateColorAsState(if (selected) Pal.gold else Pal.line, tween(200), label = "tileBorder")
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = 400f), label = "tileLift")
    Column(
        modifier
            .graphicsLayer { translationY = -6.dp.toPx() * lift; scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift }
            .pressClick(true, onClick)
            .clip(TileShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(if (selected) 2.dp else 1.dp, border, TileShape)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(f.emoji, fontSize = 30.sp) }
        Text(f.title, style = serif(13f), textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        Text(
            if (selected) "✔ выбрана" else " ",
            style = body(10f, Pal.gold, FontWeight.SemiBold), maxLines = 1
        )
    }
}

/** Лента карт выбранной колоды; нажатие на карту показывает её эффект. */
@Composable
private fun DeckPreview(faction: Faction) {
    var picked by remember(faction) { mutableStateOf<CardTemplate?>(null) }
    Column(Modifier.fillMaxWidth().glass(TileShape).padding(vertical = 10.dp)) {
        Text(
            faction.blurb, style = body(12f, Pal.textDim), fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Crossfade(targetState = faction, animationSpec = tween(220), label = "deck") { f ->
            // при смене колоды лента плавно заменяется
            val cards = remember(f) { CardLibrary.deck(f).map { it to it.toView() } }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cards, key = { it.first.id }) { (t, v) ->
                    BattleCard(
                        card = v, width = 64.dp,
                        mark = if (picked?.id == t.id) CardMark.SELECTED else CardMark.NONE,
                        onClick = { picked = if (picked?.id == t.id) null else t }
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = picked != null,
            enter = expandVertically(tween(200)), exit = shrinkVertically(tween(200))
        ) {
            val t = picked
            Column(Modifier.padding(horizontal = 14.dp)) {
                if (t != null) {
                    Text("${t.emoji} ${t.name}  ⚔ ${t.atk}  🛡 ${t.def}", style = serif(14f))
                    Text("⚡ ${t.effect.title}: ${t.effect.text}", style = body(12f, Pal.textDim), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (picked == null) {
            Text(
                "Нажмите на карту, чтобы прочитать эффект", style = body(10f, Pal.textDim),
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    }
}

@Composable
private fun SectionBox(title: String, accent: Color = Pal.gold, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(TileShape).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = serif(15f, accent, shadow = false))
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun IpField(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Pal.textDim, fontSize = 12.sp) },
        singleLine = true,
        enabled = enabled,
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
}

/** Быстрый поиск: кнопка активна, только пока сервер запущен и найден в сети. */
@Composable
private fun OnlineBlock(vm: GameViewModel) {
    SectionBox("🌐  Онлайн: быстрый поиск") {
        val online = vm.serverOnline
        var manual by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dot by animateColorAsState(if (online) Pal.teal else Pal.red, tween(300), label = "dot")
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Text(
                if (online) "  Сервер найден" else "  Сервер не найден",
                color = if (online) Pal.teal else Pal.textDim, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        GoldButton(
            text = if (vm.isSearchingMatch) "Отменить поиск" else "⚡ Быстрый поиск",
            modifier = Modifier.fillMaxWidth(),
            enabled = online || vm.isSearchingMatch
        ) {
            if (vm.isSearchingMatch) vm.cancelOnlineSearch() else vm.startOnlineSearch()
        }
        if (vm.searchStatusText.isNotEmpty()) {
            Text(vm.searchStatusText, style = serif(12f, Pal.gold, shadow = false), modifier = Modifier.padding(top = 8.dp))
        }
        if (!online && !vm.isSearchingMatch) {
            Text(
                if (manual) "Укажите IP компьютера, на котором запущен server.py:" else "Указать IP сервера вручную",
                style = body(11f, if (manual) Pal.textDim else Pal.gold), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).clickable { manual = !manual }
            )
            AnimatedVisibility(manual, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(200))) {
                Column(Modifier.padding(top = 6.dp)) {
                    IpField("IP адрес сервера", vm.serverIp) { vm.onServerIpChange(it) }
                }
            }
        }
    }
}

/** Игра двух устройств по одной сети (общий Wi‑Fi или точка доступа), без сервера. */
@Composable
private fun LanBlock(vm: GameViewModel) {
    SectionBox("📶  Игра по одной сети", Pal.teal) {
        Text(
            "Два устройства в одной Wi‑Fi сети — или одно раздаёт точку доступа.",
            style = body(11f, Pal.textDim), textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Создать игру", Modifier.weight(1f)) { vm.openLanHost() }
            GoldButton("Подключиться", Modifier.weight(1f)) { vm.openLanJoin() }
        }
    }
}

@Composable
private fun LanHostDialog(vm: GameViewModel) {
    GameDialog(onDismiss = { vm.closeLanDialog() }) {
        Text("Игра создана", style = serif(24f, Pal.gold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            vm.lanStatus, style = body(13f, Pal.teal), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)
        )
        Text(
            "Второй игрок должен нажать «Подключиться» — это устройство появится у него в списке. " +
                "Ваша колода: ${vm.menuFaction.title}.",
            style = body(13f)
        )
        if (vm.myAddresses.isNotEmpty()) {
            Text(
                "Если устройство не нашлось в списке, пусть введёт ваш IP вручную:",
                style = body(12f, Pal.textDim), modifier = Modifier.padding(top = 10.dp)
            )
            vm.myAddresses.forEach {
                Text(it, style = body(20f, Pal.gold, FontWeight.Bold), modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        GhostButton("Отмена", Modifier.fillMaxWidth()) { vm.closeLanDialog() }
    }
}

@Composable
private fun LanJoinDialog(vm: GameViewModel) {
    GameDialog(onDismiss = { vm.closeLanDialog() }) {
        Text("Подключиться к игре", style = serif(24f, Pal.gold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "Ваша колода: ${vm.menuFaction.title}", style = body(12f, Pal.textDim), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp)
        )
        if (vm.lanHosts.isEmpty()) {
            Text(
                "Ищем созданные игры в сети…", style = body(13f, Pal.textDim), fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            vm.lanHosts.forEach { host ->
                LanHostRow(host) { vm.joinLan(host.ip) }
                Spacer(Modifier.height(6.dp))
            }
        }
        Text(
            "Не нашлось? Введите IP устройства, создавшего игру:", style = body(12f, Pal.textDim),
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )
        IpField("IP адрес", vm.lanManualIp) { vm.lanManualIp = it }
        Spacer(Modifier.height(8.dp))
        GoldButton("Подключиться по IP", Modifier.fillMaxWidth()) { vm.joinLan(vm.lanManualIp) }
        if (vm.lanStatus.isNotEmpty()) {
            Text(
                vm.lanStatus, style = body(12f, Pal.gold), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Закрыть", Modifier.fillMaxWidth()) { vm.closeLanDialog() }
    }
}

@Composable
private fun LanHostRow(host: DiscoveredHost, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val faction = Faction.values().firstOrNull { it.name == host.faction }
    Row(
        Modifier.fillMaxWidth().pressClick(true, onClick).clip(shape).background(Pal.panel2)
            .border(1.dp, Pal.teal.copy(alpha = 0.6f), shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(faction?.emoji ?: "📱", fontSize = 22.sp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(host.name, style = body(14f, Pal.text, FontWeight.Bold))
            Text("${faction?.title ?: "Игрок"} · ${host.ip}", style = body(11f, Pal.textDim))
        }
        Text("▶", color = Pal.teal, fontSize = 16.sp)
    }
}
