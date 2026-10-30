package com.cardgame.ui

import android.os.Build
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
import com.cardgame.network.AndroidNet
import com.cardgame.network.DiscoveredHost
import com.cardgame.network.GameLink
import com.cardgame.network.HostKind
import com.cardgame.network.LanClient
import com.cardgame.network.LanDiscovery
import com.cardgame.network.LanHost
import com.cardgame.network.NetConfig
import com.cardgame.network.NetworkClient
import com.cardgame.network.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.random.Random

enum class Screen { MENU, BATTLE }

enum class LanDialog { NONE, HOST, JOIN }

/** Эффект последней атаки: подсветка и «−N» над картами. */
data class HitFx(val attackerUid: Int, val targetUid: Int, val hits: Map<Int, Int>, val text: String)

/**
 * Одна и та же логика обслуживает три режима:
 *  - бот (как раньше),
 *  - быстрый поиск через сервер,
 *  - игра по одной сети (телефон-хозяин + телефон-гость).
 *
 * Сетевая партия — «синхронный запуск» одной и той же игры на двух устройствах: у обоих один сид,
 * ходы (выставил карту, закончил ход, выбрал цель, выбрал бафф) пересылаются сопернику и
 * применяются тем же движком в том же порядке. К каждому ходу прикладывается контрольная сумма
 * состояния — если суммы разошлись, партия прерывается, а не продолжается «вразнобой».
 */
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

    /** Короткая подсказка поверх боя (например, «эту цель нельзя атаковать»). */
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    private fun showToast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2200)
            toast = null
        }
    }

    var menuFaction by mutableStateOf(Faction.HUMANS)
    var showMenuDialog by mutableStateOf(false)
    var showRules by mutableStateOf(false)

    /** Сообщение на главном экране (например, «Соперник отключился»). */
    var menuMessage by mutableStateOf<String?>(null)

    // ------------------------------------------------------------ мультиплеер
    var isOnlineGame by mutableStateOf(false)
        private set
    var isSearchingMatch by mutableStateOf(false)
        private set
    var onlineGameId by mutableStateOf<String?>(null)
        private set
    var searchStatusText by mutableStateOf("")
        private set

    /** IP сервера, введённый вручную (нужен, если автопоиск не сработал). */
    var serverIp by mutableStateOf(Prefs.serverIp() ?: "192.168.1.100")
        private set

    /** Сервер найден и доступен — только тогда работает «Быстрый поиск». */
    var serverOnline by mutableStateOf(false)
        private set
    private var serverAddress: String? = null

    // игра по одной сети
    var lanDialog by mutableStateOf(LanDialog.NONE)
        private set
    var lanHosts by mutableStateOf<List<DiscoveredHost>>(emptyList())
        private set
    var lanStatus by mutableStateOf("")
        private set
    var lanManualIp by mutableStateOf("")
    var myAddresses by mutableStateOf<List<String>>(emptyList())
        private set

    private var link: GameLink? = null
    private var linkGen = 0
    private val incoming = Channel<Pair<Int, JSONObject>>(Channel.UNLIMITED)
    private val stepMutex = Mutex()

    private var game: Game? = null
    private var me: Side = Side.PLAYER
    private var job: Job? = null
    private var probeJob: Job? = null
    private var auto = false
    private val rng = Random.Default

    init {
        // Все входящие сетевые сообщения обрабатываются строго по очереди.
        viewModelScope.launch {
            for ((gen, msg) in incoming) {
                if (gen != linkGen) continue // сообщение от закрытого соединения
                try {
                    handleNetworkMessage(msg)
                } catch (e: Exception) {
                    abortOnline("Ошибка сетевой игры: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    // ------------------------------------------------------------ производные состояния

    val waitingForPlayer: Boolean
        get() = view?.let { it.phase == Phase.BATTLE && it.attackerSide == Side.PLAYER && !busy } ?: false

    val centerText: String
        get() {
            val g = view ?: return ""
            val hit = fx
            if (hit != null) return hit.text
            return when (g.phase) {
                Phase.PREP -> when {
                    g.pvp && !g.canAct -> "Соперник расставляет карты…"
                    g.isFinal -> "Финал: расставьте карты"
                    else -> "Раунд ${g.round}: расставьте карты"
                }
                Phase.BATTLE -> when {
                    g.attackerSide == Side.PLAYER -> "Выберите цель атаки"
                    g.pvp -> "Ход соперника…"
                    else -> "Ход врага…"
                }
                else -> ""
            }
        }

    private fun refresh() {
        val v = game?.snapshot(me)
        view = v
        // выбранная карта могла исчезнуть (убита, сброшена, ушла в сброс) — снимаем выделение
        val sel = selectedUid
        if (v != null && sel != null &&
            v.player.hand.none { it.uid == sel } && v.player.field.none { it.uid == sel } &&
            v.enemy.field.none { it.uid == sel }
        ) selectedUid = null
    }

    private fun resetBattleUi() {
        selectedUid = null
        fx = null
        busy = false
        overlayReady = false
        chosenIndex = null
        auto = false
        showMenuDialog = false
    }

    // ------------------------------------------------------------ старт / выход

    /** Игра против бота. */
    fun startGame() {
        job?.cancel()
        disconnectNetwork()
        isOnlineGame = false
        isSearchingMatch = false
        me = Side.PLAYER
        val enemyFaction = Faction.values().filter { it != menuFaction }.random(rng)
        game = Game(menuFaction, enemyFaction, rng)
        resetBattleUi()
        screen = Screen.BATTLE
        refresh()
    }

    fun toMenu() {
        job?.cancel()
        disconnectNetwork()
        busy = false
        isOnlineGame = false
        isSearchingMatch = false
        searchStatusText = ""
        toast = null
        showMenuDialog = false
        overlayReady = false
        lanDialog = LanDialog.NONE
        lanStatus = ""
        screen = Screen.MENU
    }

    /** Прервать сетевую партию с сообщением на главном экране. */
    private fun abortOnline(message: String) {
        menuMessage = message
        toMenu()
    }

    // ------------------------------------------------------------ главное меню: проверка сервера и поиск устройств

    fun onServerIpChange(value: String) {
        serverIp = value
        Prefs.saveServerIp(value)
    }

    /** Пока открыто меню, раз в несколько секунд ищем сервер и созданные игры в сети. */
    fun startMenuProbe() {
        if (probeJob?.isActive == true) return
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val manual = serverIp.trim()
                val extras = buildList {
                    if (manual.isNotEmpty()) add(manual)
                    addAll(AndroidNet.gatewayCandidates())
                }
                val found = LanDiscovery.scan(extras, 1200)
                val server = found.firstOrNull { it.kind == HostKind.SERVER }
                var address = server?.ip
                if (address == null && manual.isNotEmpty() &&
                    LanDiscovery.isPortOpen(manual, NetConfig.SERVER_PORT, 800)
                ) address = manual
                val hosts = found.filter { it.kind == HostKind.LAN_HOST }
                withContext(Dispatchers.Main) {
                    serverAddress = address
                    serverOnline = address != null
                    lanHosts = hosts
                }
                delay(2500)
            }
        }
    }

    fun stopMenuProbe() {
        probeJob?.cancel()
        probeJob = null
    }

    // ------------------------------------------------------------ быстрый поиск через сервер

    fun startOnlineSearch() {
        if (isSearchingMatch) return
        val address = serverAddress ?: return
        job?.cancel()
        menuMessage = null
        isSearchingMatch = true
        searchStatusText = "Подключение к серверу..."
        val client = NetworkClient(newListener())
        link = client
        client.connect(address)
        client.findMatch(menuFaction.name)
    }

    fun cancelOnlineSearch() {
        disconnectNetwork()
        isSearchingMatch = false
        searchStatusText = ""
    }

    // ------------------------------------------------------------ игра по одной сети

    fun openLanHost() {
        menuMessage = null
        disconnectNetwork()
        myAddresses = LanDiscovery.localAddresses()
        lanDialog = LanDialog.HOST
        lanStatus = "Ожидание второго игрока…"
        val host = LanHost(newListener())
        link = host
        val name = (Build.MODEL ?: "Телефон").trim().ifEmpty { "Телефон" }
        host.start(name, menuFaction.name)
    }

    fun openLanJoin() {
        menuMessage = null
        disconnectNetwork()
        lanDialog = LanDialog.JOIN
        lanStatus = ""
    }

    fun joinLan(ip: String) {
        val target = ip.trim()
        if (target.isEmpty()) {
            lanStatus = "Введите IP-адрес устройства, которое создало игру"
            return
        }
        link?.close()
        linkGen++
        lanStatus = "Подключение к $target…"
        val client = LanClient(newListener())
        link = client
        client.join(target, menuFaction.name)
    }

    fun closeLanDialog() {
        disconnectNetwork()
        lanDialog = LanDialog.NONE
        lanStatus = ""
    }

    // ------------------------------------------------------------ сеть: общая часть

    private fun newListener(): (JSONObject) -> Unit {
        val gen = ++linkGen
        return { json -> incoming.trySend(gen to json) }
    }

    private fun disconnectNetwork() {
        linkGen++ // сообщения старого соединения будут отброшены
        (link as? NetworkClient)?.cancelSearch()
        link?.close()
        link = null
        onlineGameId = null
    }

    private fun sendEvent(type: String, build: JSONObject.() -> Unit = {}) {
        val l = link ?: return
        val g = game ?: return
        val data = JSONObject().apply(build)
        data.put("h", g.stateHash()) // контрольная сумма ПОСЛЕ применения хода
        l.send(type, data)
    }

    private suspend fun handleNetworkMessage(json: JSONObject) {
        when (json.optString("type")) {
            "QUEUE_STATUS" -> {
                val status = json.optString("status")
                searchStatusText = if (status == "searching") "Поиск противника..." else ""
            }
            "MATCH_FOUND" -> startNetworkGame(json)
            "OPPONENT_EVENT" -> handleOpponentEvent(json.getJSONObject("payload"))
            "OPPONENT_DISCONNECTED" -> {
                val over = game?.phase == Phase.GAME_OVER
                if (screen == Screen.BATTLE && !over) abortOnline("Соперник отключился")
            }
            "CONNECTION_ERROR" -> {
                val text = json.optString("message", "Ошибка сети")
                if (screen == Screen.BATTLE) {
                    if (game?.phase != Phase.GAME_OVER) abortOnline("Связь потеряна: $text")
                } else if (lanDialog != LanDialog.NONE) {
                    lanStatus = text
                } else {
                    isSearchingMatch = false
                    searchStatusText = text
                }
            }
        }
    }

    /** Начало сетевой партии: у обоих устройств одинаковый сид и состав сторон. */
    private fun startNetworkGame(json: JSONObject) {
        val role = json.optString("role")
        val myFaction: Faction
        val foeFaction: Faction
        val seed: Long
        try {
            myFaction = Faction.valueOf(json.getString("my_faction"))
            foeFaction = Faction.valueOf(json.getString("enemy_faction"))
            seed = json.getLong("seed")
        } catch (e: Exception) {
            abortOnline("Некорректный ответ сервера: ${e.message}")
            return
        }
        me = if (role == "PLAYER_2") Side.ENEMY else Side.PLAYER
        // Состав сторон в движке один для обоих: PLAYER — это «PLAYER_1», ENEMY — «PLAYER_2».
        val firstFaction = if (me == Side.PLAYER) myFaction else foeFaction
        val secondFaction = if (me == Side.PLAYER) foeFaction else myFaction

        job?.cancel()
        (link as? NetworkClient)?.gameId = json.optString("game_id")
        onlineGameId = json.optString("game_id")
        isOnlineGame = true
        isSearchingMatch = false
        searchStatusText = ""
        lanDialog = LanDialog.NONE
        lanStatus = ""
        menuMessage = null

        game = Game(firstFaction, secondFaction, Random(seed), pvp = true)
        resetBattleUi()
        screen = Screen.BATTLE
        refresh()
    }

    /** Ход соперника. Применяется тем же движком; затем сверяется контрольная сумма. */
    private suspend fun handleOpponentEvent(payload: JSONObject) {
        val g = game ?: return
        if (!g.pvp) return
        val opp = me.other
        val type = payload.optString("type")
        val data = payload.optJSONObject("data") ?: JSONObject()
        var report: com.cardgame.core.StepReport? = null

        val applied = when (type) {
            "PLAY" -> g.playFromHand(opp, data.getInt("uid"))
            "DISCARD" -> g.discardFromHand(opp, data.getInt("uid"))
            "REMOVE" -> g.removeFromField(opp, data.getInt("uid"))
            "SWAP" -> g.swap(opp, data.getInt("hand_uid"), data.getInt("field_uid"))
            "END_PREP" -> if (g.canAct(opp)) {
                g.endPrep(opp); true
            } else false
            "ATTACK" -> {
                val attacker = g.currentAttacker()
                val target = attacker?.let { a ->
                    g.validTargets(a).firstOrNull { it.uid == data.getInt("target_uid") }
                }
                if (attacker != null && attacker.owner == opp && target != null) {
                    report = g.performAttack(attacker, target)
                    true
                } else false
            }
            "CHOICE" -> if (g.phase == Phase.CHOICE && g.roundWinner == opp && g.chosenOption == null) {
                g.chooseOption(data.getInt("option_index"), opp); true
            } else false
            else -> true
        }

        if (!applied || (data.has("h") && data.getInt("h") != g.stateHash())) {
            abortOnline("Партии разошлись (событие «$type»). Сыграйте заново.")
            return
        }

        when (type) {
            "ATTACK" -> {
                busy = true
                report?.let { animate(it) }
                loop(g)
            }
            "END_PREP" -> {
                selectedUid = null
                refresh()
                if (g.phase == Phase.BATTLE) {
                    busy = true
                    loop(g)
                }
            }
            "CHOICE" -> {
                overlayReady = false
                chosenIndex = null
                refresh()
            }
            else -> refresh()
        }
    }

    // ------------------------------------------------------------ подготовка

    fun onHandTap(uid: Int) {
        selectedUid = if (selectedUid == uid) null else uid
    }

    fun onPlayerFieldTap(uid: Int) {
        val g = game ?: return
        val sel = selectedUid
        if (g.phase == Phase.PREP && !busy && sel != null && g.board(me).hand.any { it.uid == sel }) {
            if (g.swap(me, sel, uid)) {
                sendEvent("SWAP") {
                    put("hand_uid", sel)
                    put("field_uid", uid)
                }
            }
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
        if (g.phase == Phase.PREP && g.playFromHand(me, sel)) {
            sendEvent("PLAY") { put("uid", sel) }
            selectedUid = null
            refresh()
        }
    }

    /** Сбросить карту из руки (в следующем раунде придёт новая) или убрать её с поля. */
    fun discardSelected() {
        val g = game ?: return
        val sel = selectedUid ?: return
        if (g.phase != Phase.PREP) return
        when {
            g.discardFromHand(me, sel) -> sendEvent("DISCARD") { put("uid", sel) }
            g.removeFromField(me, sel) -> sendEvent("REMOVE") { put("uid", sel) }
            else -> return
        }
        selectedUid = null
        refresh()
    }

    fun onEndTurn() {
        val g = game ?: return
        if (busy || !g.canAct(me)) return
        g.endPrep(me)
        sendEvent("END_PREP")
        selectedUid = null
        auto = false
        refresh()
        if (g.phase == Phase.BATTLE) {
            busy = true
            job = viewModelScope.launch { loop(g) }
        }
    }

    // ------------------------------------------------------------ бой

    private fun playerAttack(targetUid: Int) {
        val g = game ?: return
        val attacker = g.currentAttacker() ?: return
        if (attacker.owner != me) return
        val target = g.validTargets(attacker).firstOrNull { it.uid == targetUid }
        if (target == null) {
            showToast("Эту карту сейчас нельзя атаковать — выберите цель, подсвеченную красным")
            return
        }
        busy = true
        selectedUid = null
        // Удар применяется сразу (до отправки сопернику), а анимация идёт отдельно.
        val rep = g.performAttack(attacker, target)
        sendEvent("ATTACK") { put("target_uid", targetUid) }
        job?.cancel()
        job = viewModelScope.launch {
            animate(rep)
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
        if (g.pvp || g.phase != Phase.BATTLE || busy) return
        auto = true
        busy = true
        selectedUid = null
        job?.cancel()
        job = viewModelScope.launch { loop(g) }
    }

    private suspend fun loop(g: Game) {
        while (true) {
            if (g.phase != Phase.BATTLE) {
                // Дать увидеть последний удар, затем показать итог раунда.
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
            if (attacker.owner == me && !auto) {
                busy = false
                refresh()
                return
            }
            if (g.pvp && attacker.owner != me) {
                // В сетевой игре ждём ход настоящего соперника.
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
        animate(g.performAttack(attacker, target))
    }

    /** Подсветка удара и «−N» над картами. Анимации идут строго одна за другой. */
    private suspend fun animate(rep: com.cardgame.core.StepReport) {
        stepMutex.withLock {
            val hits = HashMap<Int, Int>()
            rep.hits.forEach { hits[it.uid] = (hits[it.uid] ?: 0) + it.amount }
            val died = if (rep.targetDied) " ☠" else ""
            fx = HitFx(
                rep.attackerUid, rep.targetUid, hits,
                "${rep.attackerName} → ${rep.targetName}: −${rep.primaryDamage}$died"
            )
            refresh()
            delay(800)
            fx = null
        }
    }

    // ------------------------------------------------------------ выбор после раунда

    fun pickOption(i: Int) {
        chosenIndex = i
    }

    fun applyChoice() {
        val g = game ?: return
        val i = chosenIndex ?: return
        g.chooseOption(i, me)
        sendEvent("CHOICE") { put("option_index", i) }
        chosenIndex = null
        overlayReady = false
        refresh()
    }

    /** Только для игры с ботом: подтвердить выбор, который сделал бот. */
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
