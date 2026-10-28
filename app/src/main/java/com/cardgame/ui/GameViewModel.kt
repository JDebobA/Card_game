package com.cardgame.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgame.core.Card
import com.cardgame.core.Faction
import com.cardgame.core.Game
import com.cardgame.core.GameView
import com.cardgame.core.Phase
import com.cardgame.core.Side
import com.cardgame.network.NetworkClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

enum class Screen { MENU, BATTLE }

/** Эффект последней атаки: подсветка и «−N» над картами. */
data class HitFx(val attackerUid: Int, val targetUid: Int, val hits: Map<Int, Int>, val text: String)

class GameViewModel : ViewModel() {

    var screen by mutableStateOf(Screen.MENU)
        private set
    var view by mutableStateOf<GameView?>(null)
        private set
    var selectedUid by mutableStateOf<Int?>(null)
        private set
    var fx by mutableStateOf<HitFx?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var overlayReady by mutableStateOf(false)
        private set
    var chosenIndex by mutableStateOf<Int?>(null)
        private set

    var menuFaction by mutableStateOf(Faction.HUMANS)
    var showMenuDialog by mutableStateOf(false)
    var showRules by mutableStateOf(false)

    // ------------------------------------------------------------ мультиплеер
    var isOnlineGame by mutableStateOf(false)
        private set
    var isSearchingMatch by mutableStateOf(false)
        private set
    var onlineGameId by mutableStateOf<String?>(null)
        private set
    var serverIp by mutableStateOf("192.168.1.100")
    var searchStatusText by mutableStateOf("")
        private set

    private var netClient: NetworkClient? = null
    private var game: Game? = null
    private var job: Job? = null
    private var auto = false
    private val rng = Random.Default

    // ------------------------------------------------------------ производные состояния

    val waitingForPlayer: Boolean
        get() = view?.let { it.phase == Phase.BATTLE && it.attackerSide == Side.PLAYER && !busy } ?: false

    val centerText: String
        get() {
            val g = view ?: return ""
            val hit = fx
            if (hit != null) return hit.text
            return when (g.phase) {
                Phase.PREP ->
                    if (g.isFinal) "Финал: расставьте карты" else "Раунд ${g.round}: расставьте карты"
                Phase.BATTLE ->
                    if (g.attackerSide == Side.PLAYER) "Выберите цель атаки" else "Ход врага…"
                else -> ""
            }
        }

    // ------------------------------------------------------------ старт / выход

    fun startGame() {
        job?.cancel()
        disconnectNetwork()
        isOnlineGame = false
        val enemyFaction = Faction.values().filter { it != menuFaction }.random(rng)
        game = Game(menuFaction, enemyFaction, rng)
        selectedUid = null
        fx = null
        busy = false
        overlayReady = false
        chosenIndex = null
        auto = false
        showMenuDialog = false
        screen = Screen.BATTLE
        refresh()
    }

    fun toMenu() {
        job?.cancel()
        disconnectNetwork()
        busy = false
        isOnlineGame = false
        isSearchingMatch = false
        showMenuDialog = false
        screen = Screen.MENU
    }

    private fun refresh() {
        view = game?.snapshot()
    }

    // ------------------------------------------------------------ сеть

    fun startOnlineSearch() {
        if (isSearchingMatch) return
        job?.cancel()
        isSearchingMatch = true
        searchStatusText = "Подключение к серверу..."

        netClient = NetworkClient { json ->
            viewModelScope.launch {
                handleNetworkMessage(json)
            }
        }
        netClient?.connect(serverIp)
        netClient?.findMatch(menuFaction.name)
    }

    fun cancelOnlineSearch() {
        disconnectNetwork()
        isSearchingMatch = false
        searchStatusText = ""
    }

    private fun disconnectNetwork() {
        netClient?.cancelSearch()
        netClient?.close()
        netClient = null
        onlineGameId = null
    }

    private fun handleNetworkMessage(json: JSONObject) {
        when (json.optString("type")) {
            "QUEUE_STATUS" -> {
                val status = json.optString("status")
                searchStatusText = if (status == "searching") "Поиск противника..." else ""
            }
            "MATCH_FOUND" -> {
                val gId = json.getString("game_id")
                val enemyFactionStr = json.getString("enemy_faction")
                val seed = json.getLong("seed")
                val enemyFaction = Faction.valueOf(enemyFactionStr)

                onlineGameId = gId
                isOnlineGame = true
                isSearchingMatch = false
                searchStatusText = ""

                job?.cancel()
                game = Game(menuFaction, enemyFaction, Random(seed))
                selectedUid = null
                fx = null
                busy = false
                overlayReady = false
                chosenIndex = null
                auto = false
                showMenuDialog = false
                screen = Screen.BATTLE
                refresh()
            }
            "OPPONENT_EVENT" -> {
                val payload = json.getJSONObject("payload")
                handleOpponentAction(payload)
            }
            "OPPONENT_DISCONNECTED" -> {
                toMenu()
            }
            "CONNECTION_ERROR" -> {
                isSearchingMatch = false
                searchStatusText = json.optString("message", "Ошибка сети")
            }
        }
    }

    private fun handleOpponentAction(payload: JSONObject) {
        when (payload.optString("type")) {
            "ATTACK" -> {
                val targetUid = payload.getJSONObject("data").getInt("target_uid")
                val g = game ?: return
                val attacker = g.currentAttacker() ?: return
                if (attacker.owner != Side.ENEMY) return
                val target = g.validTargets(attacker).firstOrNull { it.uid == targetUid } ?: return

                busy = true
                job?.cancel()
                job = viewModelScope.launch {
                    runStep(g, attacker, target)
                    loop(g)
                }
            }
            "CHOICE" -> {
                val optIndex = payload.getJSONObject("data").getInt("option_index")
                val g = game ?: return
                if (g.phase == Phase.CHOICE && g.roundWinner == Side.ENEMY) {
                    g.chooseOption(optIndex)
                    overlayReady = false
                    refresh()
                }
            }
        }
    }

    // ------------------------------------------------------------ подготовка

    fun onHandTap(uid: Int) {
        selectedUid = if (selectedUid == uid) null else uid
    }

    fun onPlayerFieldTap(uid: Int) {
        val g = game ?: return
        val sel = selectedUid
        if (g.phase == Phase.PREP && !busy && sel != null && g.player.hand.any { it.uid == sel }) {
            g.swap(Side.PLAYER, sel, uid)
            selectedUid = null
            refresh()
            return
        }
        selectedUid = if (sel == uid) null else uid
    }

    fun onEnemyFieldTap(uid: Int) {
        if (waitingForPlayer) {
            playerAttack(uid)
            return
        }
        selectedUid = if (selectedUid == uid) null else uid
    }

    fun onEmptySlotTap() = playSelected()

    fun playSelected() {
        val g = game ?: return
        val sel = selectedUid ?: return
        if (g.phase == Phase.PREP && g.playFromHand(Side.PLAYER, sel)) {
            selectedUid = null
            refresh()
        }
    }

    /** Сбросить карту из руки (в следующем раунде придёт новая) или убрать её с поля. */
    fun discardSelected() {
        val g = game ?: return
        val sel = selectedUid ?: return
        if (g.phase != Phase.PREP) return
        if (g.discardFromHand(Side.PLAYER, sel) || g.removeFromField(Side.PLAYER, sel)) {
            selectedUid = null
            refresh()
        }
    }

    fun onEndTurn() {
        val g = game ?: return
        if (g.phase != Phase.PREP || busy) return
        g.endPrep()
        selectedUid = null
        auto = false
        busy = true
        refresh()
        job = viewModelScope.launch { loop(g) }
    }

    // ------------------------------------------------------------ бой

    private fun playerAttack(targetUid: Int) {
        val g = game ?: return
        val attacker = g.currentAttacker() ?: return
        if (attacker.owner != Side.PLAYER) return
        val target = g.validTargets(attacker).firstOrNull { it.uid == targetUid } ?: return

        if (isOnlineGame && onlineGameId != null) {
            val data = JSONObject().apply { put("target_uid", targetUid) }
            netClient?.sendAction(onlineGameId!!, "ATTACK", data)
        }

        busy = true
        selectedUid = null
        job?.cancel()
        job = viewModelScope.launch {
            runStep(g, attacker, target)
            loop(g)
        }
    }

    fun onRandomTarget() {
        val g = game ?: return
        if (!waitingForPlayer) return
        val attacker = g.currentAttacker() ?: return
        val target = g.validTargets(attacker).randomOrNull(rng) ?: return
        playerAttack(target.uid)
    }

    fun onAutoBattle() {
        val g = game ?: return
        if (g.phase != Phase.BATTLE || busy) return
        auto = true
        busy = true
        selectedUid = null
        job?.cancel()
        job = viewModelScope.launch { loop(g) }
    }

    private suspend fun loop(g: Game) {
        while (true) {
            if (g.phase != Phase.BATTLE) {
                delay(600)
                fx = null
                auto = false
                overlayReady = true
                busy = false
                refresh()
                return
            }
            val attacker = g.currentAttacker()
            if (attacker == null) {
                busy = false
                refresh()
                return
            }
            if (attacker.owner == Side.PLAYER && !auto) {
                busy = false
                refresh()
                return
            }
            if (isOnlineGame && attacker.owner == Side.ENEMY) {
                // В онлайн-режиме ждем выбор цели от реального соперника
                busy = false
                refresh()
                return
            }
            refresh()
            delay(450)
            val target = g.validTargets(attacker).randomOrNull(rng)
            if (target == null) {
                busy = false
                refresh()
                return
            }
            runStep(g, attacker, target)
        }
    }

    private suspend fun runStep(g: Game, attacker: Card, target: Card) {
        val rep = g.performAttack(attacker, target)
        val hits = HashMap<Int, Int>()
        rep.hits.forEach { hits[it.uid] = (hits[it.uid] ?: 0) + it.amount }
        val died = if (rep.targetDied) " ☠" else ""
        fx = HitFx(rep.attackerUid, rep.targetUid, hits, "${rep.attackerName} → ${rep.targetName}: −${rep.primaryDamage}$died")
        refresh()
        delay(800)
        fx = null
    }

    // ------------------------------------------------------------ выбор после раунда

    fun pickOption(i: Int) {
        chosenIndex = i
    }

    fun applyChoice() {
        val g = game ?: return
        val i = chosenIndex ?: return

        if (isOnlineGame && onlineGameId != null) {
            val data = JSONObject().apply { put("option_index", i) }
            netClient?.sendAction(onlineGameId!!, "CHOICE", data)
        }

        g.chooseOption(i)
        chosenIndex = null
        overlayReady = false
        refresh()
    }

    fun continueAfterEnemyChoice() {
        game?.acknowledgeChoice()
        overlayReady = false
        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        disconnectNetwork()
    }
}