package com.cardgame.core

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Чистая игровая логика (без Android). Поток партии:
 *
 *  PREP   — игрок выставляет/меняет карты, затем «Закончить ход»
 *  BATTLE — карты по очереди (по убыванию атаки) бьют карты врага
 *  CHOICE — победитель раунда выбирает один из 3 вариантов баффов/дебаффов
 *  ... так 3 раза, затем 4-й «раунд» = ФИНАЛ: бой до последней карты
 *  GAME_OVER
 */
class Game(
    playerFaction: Faction,
    enemyFaction: Faction,
    private val rng: Random = Random.Default,
    /** true — оба соперника живые люди (сетевая игра): бот не ходит, подготовка идёт по очереди. */
    val pvp: Boolean = false
) {
    val player = Board(Side.PLAYER, playerFaction)
    val enemy = Board(Side.ENEMY, enemyFaction)

    var phase: Phase = Phase.PREP
        private set
    var round: Int = 1
        private set
    var winner: Side? = null
        private set
    var roundWinner: Side? = null
        private set
    var battlePass: Int = 1
        private set
    var choiceOptions: List<BuffOption> = emptyList()
        private set
    var chosenOption: BuffOption? = null
        private set
    var note: String? = null
        private set

    /** Только для pvp: кто сейчас расставляет карты. Первым ходит PLAYER в нечётных раундах, ENEMY — в чётных. */
    var prepTurn: Side = Side.PLAYER
        private set
    private val prepFirst: Side get() = if (round % 2 == 1) Side.PLAYER else Side.ENEMY

    val isFinal: Boolean get() = round == FINAL_ROUND

    private val damageDealt = mutableMapOf(Side.PLAYER to 0, Side.ENEMY to 0)
    private val queue = ArrayList<Card>()
    private var lastActor: Side = Side.PLAYER
    private var uidCounter = 1

    init {
        for (b in listOf(player, enemy)) {
            CardLibrary.deck(b.faction).forEach { b.draw.add(Card(uidCounter++, it, b.side)) }
            b.draw.shuffle(rng)
            drawUp(b)
        }
        if (pvp) prepTurn = prepFirst else botPrep()
    }

    fun board(side: Side): Board = if (side == Side.PLAYER) player else enemy

    // ======================= Характеристики =======================

    private fun fieldOf(side: Side): List<Card> = board(side).field.filter { it.alive }

    /** Атака карты с учётом эффектов. target нужен только для условий «против цели». */
    fun attackOf(card: Card, target: Card? = null): Int {
        var a = card.baseAtk + card.atkBonus
        val b = board(card.owner)
        if (card in b.field) {
            val own = b.field.filter { it.alive }
            val foes = fieldOf(card.owner.other)
            val e = card.effect
            when (e.kind) {
                EffectKind.ATK_PER_ALLY -> a += e.a * (own.size - 1)
                EffectKind.ATK_IF_FIELD_COUNT -> if (own.size == e.b) a += e.a
                EffectKind.ATK_IF_FIELD_AT_MOST -> if (own.size <= e.b) a += e.a
                EffectKind.ATK_IF_ENEMY_MORE -> if (foes.size > own.size) a += e.a
                EffectKind.ATK_IF_ALONE -> if (own.size == 1) a += e.a
                EffectKind.ATK_WHEN_WOUNDED -> if (card.damage > 0) a += e.a
                EffectKind.ATK_VS_WEAK -> if (target != null && remaining(target) <= e.b) a += e.a
                else -> Unit
            }
            for (ally in own) {
                if (ally !== card && ally.effect.kind == EffectKind.ALLY_ATK_AURA) a += ally.effect.a
            }
        }
        return max(0, a)
    }

    /** Максимальная защита (с условными бонусами). */
    fun maxDefOf(card: Card): Int {
        var d = card.baseDef
        val b = board(card.owner)
        if (card in b.field) {
            val own = b.field.filter { it.alive }
            val e = card.effect
            when (e.kind) {
                EffectKind.DEF_PER_ALLY -> d += e.a * (own.size - 1)
                EffectKind.DEF_IF_FIELD_COUNT -> if (own.size == e.b) d += e.a
                else -> Unit
            }
        }
        return max(1, d)
    }

    private fun rawRemaining(card: Card): Int = maxDefOf(card) - card.damage

    /** Текущая защита: максимум минус накопленный урон. Навсегда, пока карту не заменят. */
    fun remaining(card: Card): Int = if (!card.alive) 0 else max(1, rawRemaining(card))

    // ======================= Подготовка =======================

    /** Уничтоженные карты уходят с поля в сброс; вернутся свежими, когда колода закончится. */
    private fun bury() {
        for (b in listOf(player, enemy)) {
            val dead = b.field.filter { !it.alive }
            if (dead.isNotEmpty()) {
                b.field.removeAll { !it.alive }
                b.discard.addAll(dead)
            }
        }
    }

    /** Добор карт в руку до limit. Если в руке уже limit и больше — ничего не выдаётся. */
    private fun drawUp(b: Board, limit: Int = HAND_SIZE) {
        while (b.hand.size < limit) {
            if (b.draw.isEmpty()) {
                if (b.discard.isEmpty()) return
                // Сброс замешивается обратно: «свежие» карты без ран.
                for (c in b.discard) {
                    c.damage = 0
                    c.atkBonus = 0
                    c.alive = true
                }
                b.draw.addAll(b.discard.shuffled(rng))
                b.discard.clear()
            }
            b.hand.add(b.draw.removeAt(b.draw.size - 1))
        }
    }

    /** Можно ли сейчас менять карты этой стороны. */
    fun canAct(side: Side): Boolean = phase == Phase.PREP && (!pvp || side == prepTurn)

    fun playFromHand(side: Side, uid: Int): Boolean {
        if (!canAct(side)) return false
        val b = board(side)
        val c = b.hand.firstOrNull { it.uid == uid } ?: return false
        if (b.field.size >= FIELD_SIZE) return false
        b.hand.remove(c)
        b.field.add(c)
        onPlay(c)
        return true
    }

    fun discardFromHand(side: Side, uid: Int): Boolean {
        if (!canAct(side)) return false
        val b = board(side)
        val c = b.hand.firstOrNull { it.uid == uid } ?: return false
        b.hand.remove(c)
        b.discard.add(c)
        return true
    }

    fun removeFromField(side: Side, uid: Int): Boolean {
        if (!canAct(side)) return false
        val b = board(side)
        val c = b.field.firstOrNull { it.uid == uid } ?: return false
        b.field.remove(c)
        b.discard.add(c)
        return true
    }

    /** Убрать карту с поля (в сброс) и выставить на её место карту из руки. */
    fun swap(side: Side, handUid: Int, fieldUid: Int): Boolean {
        if (!canAct(side)) return false
        val b = board(side)
        if (b.hand.none { it.uid == handUid }) return false
        if (!removeFromField(side, fieldUid)) return false
        return playFromHand(side, handUid)
    }

    private fun onPlay(card: Card) {
        val e = card.effect
        val rep = StepReport(card.uid, card.uid, card.name, card.name)
        when (e.kind) {
            EffectKind.PLAY_DAMAGE_RANDOM -> {
                val t = fieldOf(card.owner.other).randomOrNull(rng)
                if (t != null) {
                    note = "${card.name} ранит ${t.name} (−${e.a})"
                    dealDamage(card, card.owner, t, e.a, rep, false)
                }
            }
            EffectKind.PLAY_BUFF_ALLIES -> {
                val allies = fieldOf(card.owner).filter { it !== card }
                allies.forEach { it.atkBonus += e.a }
                if (allies.isNotEmpty()) note = "${card.name}: союзники получают +${e.a} атаки"
            }
            else -> Unit
        }
        // Погибшие во время подготовки сразу уходят с поля.
        bury()
    }

    private fun value(c: Card): Int = c.baseAtk + c.atkBonus + remaining(c)

    /** Бот: убирает сильно раненых, заполняет поле лучшими картами, заменяет слабейшую. */
    private fun botPrep() {
        val b = enemy
        for (c in b.field.toList()) {
            if (b.hand.isNotEmpty() && rawRemaining(c) <= 2) {
                b.field.remove(c)
                b.discard.add(c)
            }
        }
        while (b.field.size < FIELD_SIZE && b.hand.isNotEmpty()) {
            val best = b.hand.maxByOrNull { value(it) } ?: break
            playFromHand(Side.ENEMY, best.uid)
        }
        repeat(2) {
            val weakest = b.field.minByOrNull { value(it) }
            val best = b.hand.maxByOrNull { value(it) }
            if (weakest != null && best != null && value(best) >= value(weakest) + 3) {
                swap(Side.ENEMY, best.uid, weakest.uid)
            }
        }
    }

    /**
     * Закончить подготовку. В обычной игре бой начинается сразу. В pvp первый игрок
     * передаёт ход второму, а когда заканчивает второй — начинается бой.
     */
    fun endPrep(side: Side = Side.PLAYER) {
        if (phase != Phase.PREP) return
        if (pvp) {
            if (side != prepTurn) return
            if (side == prepFirst) {
                prepTurn = side.other
                note = null
                return
            }
        }
        note = null
        startBattle()
    }

    // ======================= Бой =======================

    private fun startBattle() {
        damageDealt[Side.PLAYER] = 0
        damageDealt[Side.ENEMY] = 0
        battlePass = 1
        roundWinner = null
        phase = Phase.BATTLE
        buildQueue()
        afterStep()
    }

    /** Очередь: «ходит раньше всех», затем по убыванию атаки; равные — случайно. */
    private fun buildQueue() {
        bury()
        val cards = (player.field + enemy.field).shuffled(rng)
        queue.clear()
        queue.addAll(
            cards.sortedWith(
                compareByDescending<Card> { if (it.effect.kind == EffectKind.SWIFT) 1 else 0 }
                    .thenByDescending { attackOf(it) }
            )
        )
    }

    fun currentAttacker(): Card? {
        if (phase != Phase.BATTLE) return null
        queue.removeAll { !it.alive }
        return queue.firstOrNull()
    }

    /** Кого можно атаковать: если у врага есть «провокация», то только её носителей. */
    fun validTargets(attacker: Card): List<Card> {
        val foes = fieldOf(attacker.owner.other)
        val taunts = foes.filter { it.effect.kind == EffectKind.TAUNT }
        return if (taunts.isNotEmpty()) taunts else foes
    }

    fun performAttack(attacker: Card, target: Card): StepReport {
        check(phase == Phase.BATTLE) { "Not in battle" }
        val rep = StepReport(attacker.uid, target.uid, attacker.name, target.name)
        queue.remove(attacker)
        lastActor = attacker.owner
        strike(attacker, target, attackOf(attacker, target), rep)
        if (attacker.alive && attacker.effect.kind == EffectKind.SECOND_STRIKE) {
            val t2 = validTargets(attacker).randomOrNull(rng)
            if (t2 != null) {
                rep.log += "${attacker.name} бьёт второй раз"
                strike(attacker, t2, max(1, attackOf(attacker, t2) / 2), rep)
            }
        }
        afterStep()
        return rep
    }

    private fun strike(attacker: Card, target: Card, amount: Int, rep: StepReport) {
        rep.log += "${attacker.name} → ${target.name}"
        dealDamage(attacker, attacker.owner, target, amount, rep, true)
        if (!attacker.alive) return
        val e = attacker.effect
        when (e.kind) {
            EffectKind.ATTACK_SPLASH -> {
                val other = fieldOf(target.owner).filter { it !== target }.randomOrNull(rng)
                if (other != null) dealDamage(attacker, attacker.owner, other, e.a, rep, false)
            }
            EffectKind.HIT_DRAIN_ATK -> if (target.alive) {
                target.atkBonus -= e.a
                rep.log += "${target.name} теряет ${e.a} атаки"
            }
            else -> Unit
        }
    }

    private fun dealDamage(
        source: Card?, dealer: Side, target: Card, amount: Int, rep: StepReport, isAttack: Boolean
    ): Int {
        if (!target.alive || amount <= 0) return 0
        var dmg = amount
        val te = target.effect
        if (te.kind == EffectKind.ARMOR) dmg = max(1, dmg - te.a)
        val applied = min(dmg, max(0, rawRemaining(target)))
        target.damage += dmg
        damageDealt[dealer] = damageDealt.getValue(dealer) + applied
        rep.hits += Hit(target.uid, dmg)
        if (rawRemaining(target) <= 0) kill(target, source, rep)
        if (isAttack && te.kind == EffectKind.REFLECT && source != null && source.alive) {
            rep.log += "${target.name} ранит ${source.name} шипами"
            dealDamage(target, target.owner, source, te.a, rep, false)
        }
        return dmg
    }

    private fun kill(card: Card, killer: Card?, rep: StepReport) {
        if (!card.alive) return
        card.alive = false
        rep.deaths += card.uid
        rep.log += "${card.name} уничтожен"
        if (killer != null && killer.alive && killer.effect.kind == EffectKind.KILL_GAIN_ATK) {
            killer.atkBonus += killer.effect.a
            rep.log += "${killer.name} получает +${killer.effect.a} атаки"
        }
        val e = card.effect
        val owner = card.owner
        when (e.kind) {
            EffectKind.DEATH_DAMAGE_RANDOM ->
                fieldOf(owner.other).randomOrNull(rng)?.let { dealDamage(card, owner, it, e.a, rep, false) }
            EffectKind.DEATH_DAMAGE_ALL ->
                fieldOf(owner.other).forEach { dealDamage(card, owner, it, e.a, rep, false) }
            EffectKind.DEATH_BUFF_ALLIES ->
                fieldOf(owner).forEach { it.atkBonus += e.a }
            else -> Unit
        }
    }

    private fun afterStep() {
        queue.removeAll { !it.alive }
        val pAlive = fieldOf(Side.PLAYER).isNotEmpty()
        val eAlive = fieldOf(Side.ENEMY).isNotEmpty()
        if (isFinal) {
            if (!pAlive || !eAlive) {
                endGame(
                    when {
                        pAlive -> Side.PLAYER
                        eAlive -> Side.ENEMY
                        else -> lastActor // оба погибли одновременно — побеждает ходивший
                    }
                )
                return
            }
            if (queue.isEmpty()) newPass()
        } else if (queue.isEmpty() || !pAlive || !eAlive) {
            finishRound()
        }
    }

    /** Финал: после того как все карты сходили, начинается новый круг. */
    private fun newPass() {
        battlePass++
        val all = fieldOf(Side.PLAYER) + fieldOf(Side.ENEMY)
        val noDamagePossible = all.all { attackOf(it) == 0 }
        if (noDamagePossible || battlePass > MAX_PASSES) {
            val p = fieldOf(Side.PLAYER).sumOf { remaining(it) }
            val e = fieldOf(Side.ENEMY).sumOf { remaining(it) }
            endGame(if (p >= e) Side.PLAYER else Side.ENEMY)
            return
        }
        buildQueue()
    }

    private fun endGame(w: Side) {
        winner = w
        phase = Phase.GAME_OVER
    }

    private fun finishRound() {
        val p = damageDealt.getValue(Side.PLAYER)
        val e = damageDealt.getValue(Side.ENEMY)
        val w = when {
            p > e -> Side.PLAYER
            e > p -> Side.ENEMY
            else -> {
                val pc = fieldOf(Side.PLAYER).size
                val ec = fieldOf(Side.ENEMY).size
                when {
                    pc > ec -> Side.PLAYER
                    ec > pc -> Side.ENEMY
                    else -> if (rng.nextBoolean()) Side.PLAYER else Side.ENEMY
                }
            }
        }
        roundWinner = w
        board(w).roundsWon++
        choiceOptions = BuffFactory.roll(rng)
        chosenOption = null
        if (w == Side.ENEMY && !pvp) {
            val opt = BuffFactory.botPick(choiceOptions, rng)
            applyOption(opt, Side.ENEMY)
            chosenOption = opt
        }
        phase = Phase.CHOICE
    }

    // ======================= Выбор после раунда =======================

    /** Выбор победителя раунда. side — кто выбирает (в pvp им может быть любая из сторон). */
    fun chooseOption(index: Int, side: Side = Side.PLAYER) {
        check(phase == Phase.CHOICE && roundWinner == side && chosenOption == null) { "Not your choice" }
        val opt = choiceOptions[index]
        applyOption(opt, side)
        chosenOption = opt
        advance()
    }

    /** Подтверждение, когда выбирал бот. */
    fun acknowledgeChoice() {
        if (phase == Phase.CHOICE && roundWinner == Side.ENEMY) advance()
    }

    private fun applyOption(o: BuffOption, chooser: Side) {
        val me = board(chooser)
        val foe = board(chooser.other)
        applyMod(me, o.selfBuff)
        applyMod(me, o.selfDebuff)
        applyMod(foe, o.enemyBuff)
        applyMod(foe, o.enemyDebuff)
    }

    private fun applyMod(b: Board, m: Mod) {
        for (c in b.draw + b.hand + b.field + b.discard) {
            when (m.stat) {
                Stat.ATK -> c.baseAtk = max(0, c.baseAtk + m.amount)
                Stat.DEF -> c.baseDef = max(1, c.baseDef + m.amount)
            }
        }
        if (m.stat == Stat.ATK) b.atkMod += m.amount else b.defMod += m.amount
    }

    private fun advance() {
        // Уничтоженные карты уходят навсегда; раненые остаются с текущей защитой.
        bury()
        queue.clear()
        val choiceNote = chosenOption?.let { opt ->
            roundWinner?.let { "${board(it).faction.heroName} выбрал «${opt.title}»" }
        }
        round++
        note = if (pvp) choiceNote else null
        val limit = if (isFinal) FINAL_HAND_SIZE else HAND_SIZE
        drawUp(player, limit)
        drawUp(enemy, limit)
        phase = Phase.PREP
        if (pvp) prepTurn = prepFirst else botPrep()
    }

    // ======================= Снимок для интерфейса =======================

    private fun view(c: Card, order: Map<Int, Int>) = CardView(
        uid = c.uid, name = c.name, emoji = c.template.emoji, faction = c.template.faction,
        atk = attackOf(c), def = remaining(c), maxDef = maxDefOf(c),
        baseAtk = c.template.atk, baseDef = c.template.def,
        effectTitle = c.effect.title, effectText = c.effect.text,
        alive = c.alive, order = order[c.uid] ?: 0
    )

    private fun boardView(b: Board, order: Map<Int, Int>, hideHand: Boolean, asSide: Side): BoardView {
        val alive = b.field.filter { it.alive }
        return BoardView(
            side = asSide, faction = b.faction, heroName = b.faction.heroName,
            hand = if (hideHand) emptyList() else b.hand.map { view(it, order) },
            handCount = b.hand.size,
            field = b.field.map { view(it, order) },
            drawCount = b.draw.size + b.discard.size, // колода + сброс, который вернётся в игру
            discardCount = b.discard.size,
            roundsWon = b.roundsWon,
            totalAtk = alive.sumOf { attackOf(it) },
            totalDef = alive.sumOf { remaining(it) },
            totalMaxDef = alive.sumOf { maxDefOf(it) },
            atkMod = b.atkMod, defMod = b.defMod
        )
    }

    /**
     * Снимок для интерфейса «глазами» стороны me: в поле player всегда она сама,
     * в enemy — соперник (его рука скрыта). Так одно и то же состояние показывается
     * правильно и первому, и второму игроку в сетевой партии.
     */
    fun snapshot(me: Side = Side.PLAYER): GameView {
        val foe = me.other
        fun persp(s: Side?): Side? = when (s) {
            null -> null
            me -> Side.PLAYER
            else -> Side.ENEMY
        }
        val order = HashMap<Int, Int>()
        if (phase == Phase.BATTLE) {
            queue.filter { it.alive }.forEachIndexed { i, c -> order[c.uid] = i + 1 }
        }
        val att = if (phase == Phase.BATTLE) queue.firstOrNull { it.alive } else null
        val targets = if (att != null && att.owner == me) {
            validTargets(att).map { it.uid }.toSet()
        } else emptySet()
        return GameView(
            phase = phase, round = round, isFinal = isFinal,
            player = boardView(board(me), order, false, Side.PLAYER),
            enemy = boardView(board(foe), order, true, Side.ENEMY),
            attackerUid = att?.uid, attackerSide = persp(att?.owner), validTargets = targets,
            pass = battlePass,
            damagePlayer = damageDealt.getValue(me),
            damageEnemy = damageDealt.getValue(foe),
            roundWinner = persp(roundWinner), options = choiceOptions, chosen = chosenOption,
            winner = persp(winner), note = note,
            canAct = canAct(me), pvp = pvp
        )
    }

    /**
     * Контрольная сумма всего состояния партии. В сетевой игре оба устройства считают её
     * после одного и того же хода — если числа разные, партии разошлись.
     */
    fun stateHash(): Int {
        var h = 17
        fun mix(x: Int) { h = h * 31 + x }
        mix(phase.ordinal); mix(round); mix(battlePass); mix(prepTurn.ordinal)
        mix(player.roundsWon); mix(enemy.roundsWon)
        for (b in listOf(player, enemy)) {
            for (zone in listOf(b.draw, b.hand, b.field, b.discard)) {
                mix(-1)
                for (c in zone) {
                    mix(c.uid); mix(c.baseAtk); mix(c.baseDef); mix(c.atkBonus); mix(c.damage)
                    mix(if (c.alive) 1 else 0)
                }
            }
        }
        return h
    }

    companion object {
        const val MAX_PASSES = 40
    }
}
