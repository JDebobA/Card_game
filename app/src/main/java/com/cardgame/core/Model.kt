package com.cardgame.core

import kotlin.math.abs

const val HAND_SIZE = 5
const val FIELD_SIZE = 5
const val ROUNDS = 3
const val FINAL_ROUND = ROUNDS + 1

enum class Faction(val title: String, val heroName: String, val emoji: String, val blurb: String) {
    MONSTERS("Монстры", "Король Гнили", "💀", "Нежить, зомби и колдуны. Даже после смерти они продолжают сражаться."),
    MYTHIC("Мифические существа", "Хранитель Рощи", "🧝", "Эльфы, гномы и дварфы. Древняя магия и крепкая сталь."),
    HUMANS("Люди", "Оруженосец", "🛡️", "Воины, рабочие и стрелки. Побеждает тот, кто лучше строит порядок.")
}

enum class Side {
    PLAYER, ENEMY;

    val other: Side get() = if (this == PLAYER) ENEMY else PLAYER
}

enum class Stat(val title: String, val icon: String) {
    ATK("атака", "⚔️"),
    DEF("защита", "🛡️");

    val other: Stat get() = if (this == ATK) DEF else ATK
}

enum class Phase { PREP, BATTLE, CHOICE, GAME_OVER }

/** Тип эффекта карты. Параметры: Effect.a (величина) и Effect.b (порог/условие). */
enum class EffectKind {
    // Постоянные условные бонусы
    ATK_PER_ALLY,        // a: +атаки за каждую другую свою карту на поле
    DEF_PER_ALLY,        // a: +защиты за каждую другую свою карту на поле
    ATK_IF_FIELD_COUNT,  // a: бонус, b: ровно столько карт на своём поле
    DEF_IF_FIELD_COUNT,  // a: бонус, b: ровно столько карт на своём поле
    ATK_IF_FIELD_AT_MOST,// a: бонус, b: не больше стольких карт на своём поле
    ATK_IF_ENEMY_MORE,   // a: бонус, если у врага на поле карт больше
    ATK_IF_ALONE,        // a: бонус, если карта одна на поле
    ATK_WHEN_WOUNDED,    // a: бонус, пока карта ранена
    ATK_VS_WEAK,         // a: бонус против цели с защитой <= b
    ALLY_ATK_AURA,       // a: другие свои карты на поле получают +a атаки
    // Свойства боя
    ARMOR,               // a: входящий урон меньше на a (минимум 1)
    REFLECT,             // a: атакующий получает a урона
    TAUNT,               // враги обязаны атаковать эту карту
    SWIFT,               // ходит раньше всех
    SECOND_STRIKE,       // второй удар вдвое слабее
    // Триггеры
    HIT_DRAIN_ATK,       // a: цель навсегда теряет a атаки
    ATTACK_SPLASH,       // a: урон ещё одной случайной вражеской карте
    KILL_GAIN_ATK,       // a: +атаки за убийство
    DEATH_DAMAGE_RANDOM, // a: урон случайной вражеской карте при гибели
    DEATH_DAMAGE_ALL,    // a: урон всем вражеским картам при гибели
    DEATH_BUFF_ALLIES,   // a: союзникам +a атаки при гибели
    PLAY_DAMAGE_RANDOM,  // a: урон случайной вражеской карте при выходе на поле
    PLAY_BUFF_ALLIES     // a: союзникам на поле +a атаки при выходе на поле
}

data class Effect(
    val kind: EffectKind,
    val title: String,
    val text: String,
    val a: Int = 0,
    val b: Int = 0
)

data class CardTemplate(
    val id: String,
    val name: String,
    val faction: Faction,
    val atk: Int,
    val def: Int,
    val emoji: String,
    val effect: Effect
)

/** Живая карта в партии. */
class Card(val uid: Int, val template: CardTemplate, val owner: Side) {
    var baseAtk: Int = template.atk      // меняется баффами/дебаффами раунда
    var baseDef: Int = template.def
    var atkBonus: Int = 0                // приобретённое в бою (убийства, заражение, посмертные баффы)
    var damage: Int = 0                  // накопленный урон: защита не восстанавливается
    var alive: Boolean = true

    val effect: Effect get() = template.effect
    val name: String get() = template.name
}

class Board(val side: Side, val faction: Faction) {
    val draw = ArrayList<Card>()
    val hand = ArrayList<Card>()
    val field = ArrayList<Card>()
    val discard = ArrayList<Card>()
    var roundsWon = 0
    var atkMod = 0   // суммарные баффы/дебаффы от выбора после раундов (для отображения)
    var defMod = 0
}

// ---------- Баффы / дебаффы после раунда ----------

data class Mod(val stat: Stat, val amount: Int) {
    fun label(): String {
        val sign = if (amount > 0) "+" else "−"
        return "$sign${abs(amount)} ${stat.icon} ${stat.title}"
    }
}

/** Один вариант на выбор: бафф и дебафф себе, бафф и дебафф противнику. */
data class BuffOption(
    val id: Int,
    val title: String,
    val selfBuff: Mod,
    val selfDebuff: Mod,
    val enemyBuff: Mod,
    val enemyDebuff: Mod
) {
    /** Оценка для бота: чем выше, тем выгоднее победителю. */
    val score: Int
        get() = selfBuff.amount + selfDebuff.amount - enemyBuff.amount - enemyDebuff.amount
}

// ---------- Данные для интерфейса (неизменяемые снимки) ----------

data class CardView(
    val uid: Int,
    val name: String,
    val emoji: String,
    val faction: Faction,
    val atk: Int,
    val def: Int,
    val maxDef: Int,
    val baseAtk: Int,
    val baseDef: Int,
    val effectTitle: String,
    val effectText: String,
    val alive: Boolean,
    val order: Int
)

data class BoardView(
    val side: Side,
    val faction: Faction,
    val heroName: String,
    val hand: List<CardView>,
    val handCount: Int,
    val field: List<CardView>,
    val drawCount: Int,
    val discardCount: Int,
    val roundsWon: Int,
    val totalAtk: Int,
    val totalDef: Int,
    val totalMaxDef: Int,
    val atkMod: Int,
    val defMod: Int
)

data class GameView(
    val phase: Phase,
    val round: Int,
    val isFinal: Boolean,
    val player: BoardView,
    val enemy: BoardView,
    val attackerUid: Int?,
    val attackerSide: Side?,
    val validTargets: Set<Int>,
    val pass: Int,
    val damagePlayer: Int,
    val damageEnemy: Int,
    val roundWinner: Side?,
    val options: List<BuffOption>,
    val chosen: BuffOption?,
    val winner: Side?,
    val note: String?
)

data class Hit(val uid: Int, val amount: Int)

/** Итог одной атаки (для анимации и лога). */
class StepReport(
    val attackerUid: Int,
    val targetUid: Int,
    val attackerName: String,
    val targetName: String
) {
    val hits = mutableListOf<Hit>()
    val deaths = mutableListOf<Int>()
    val log = mutableListOf<String>()

    val primaryDamage: Int get() = hits.firstOrNull { it.uid == targetUid }?.amount ?: 0
    val targetDied: Boolean get() = deaths.contains(targetUid)
}
