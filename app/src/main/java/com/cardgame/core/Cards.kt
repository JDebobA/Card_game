package com.cardgame.core

import com.cardgame.core.EffectKind.*
import com.cardgame.core.Faction.*
import kotlin.random.Random

object CardLibrary {

    private fun card(
        id: String, name: String, f: Faction, atk: Int, def: Int, emoji: String,
        kind: EffectKind, title: String, text: String, a: Int = 0, b: Int = 0
    ) = CardTemplate(id, name, f, atk, def, emoji, Effect(kind, title, text, a, b))

    val all: List<CardTemplate> = listOf(
        // ---------------- МОНСТРЫ ----------------
        card("m1", "Скелет", MONSTERS, 3, 2, "💀", ATK_PER_ALLY, "Орда",
            "+1 атаки за каждую другую вашу карту на поле.", a = 1),
        card("m2", "Зомби", MONSTERS, 2, 6, "🧟", HIT_DRAIN_ATK, "Заражение",
            "Ударенная карта навсегда теряет 1 атаки.", a = 1),
        card("m3", "Упырь", MONSTERS, 3, 4, "👹", ATK_WHEN_WOUNDED, "Ярость",
            "+3 атаки, пока карта ранена.", a = 3),
        card("m4", "Призрак", MONSTERS, 3, 2, "👻", ARMOR, "Бесплотность",
            "Весь получаемый урон уменьшен на 2 (но не меньше 1).", a = 2),
        card("m5", "Колдун", MONSTERS, 2, 3, "🧙", ATTACK_SPLASH, "Проклятие",
            "При атаке наносит 2 урона ещё одной случайной вражеской карте.", a = 2),
        card("m6", "Вампир", MONSTERS, 4, 3, "🧛", SWIFT, "Ночной охотник",
            "Ходит раньше всех остальных карт."),
        card("m7", "Лич", MONSTERS, 3, 4, "☠️", DEATH_DAMAGE_RANDOM, "Посмертие",
            "Погибая, наносит 3 урона случайной вражеской карте.", a = 3),
        card("m8", "Костяной дракон", MONSTERS, 4, 5, "🐉", SECOND_STRIKE, "Костяное дыхание",
            "Атакует дважды; второй удар вдвое слабее."),
        card("m9", "Некромант", MONSTERS, 2, 4, "⚰️", DEATH_BUFF_ALLIES, "Зов мёртвых",
            "Погибая, даёт союзникам на поле +2 атаки.", a = 2),
        card("m10", "Банши", MONSTERS, 3, 3, "🦇", ATK_IF_FIELD_COUNT, "Предсмертный вой",
            "+3 атаки, если на вашем поле ровно 4 карты.", a = 3, b = 4),

        // ---------------- МИФИЧЕСКИЕ СУЩЕСТВА ----------------
        card("y1", "Эльф-лучник", MYTHIC, 3, 2, "🧝", ATK_IF_ENEMY_MORE, "Меткий глаз",
            "+2 атаки, если у врага на поле карт больше, чем у вас.", a = 2),
        card("y2", "Гном", MYTHIC, 2, 4, "⛏️", DEF_PER_ALLY, "Артель",
            "+1 защиты за каждую другую вашу карту на поле.", a = 1),
        card("y3", "Дварф", MYTHIC, 3, 5, "⚒️", ATK_VS_WEAK, "Секира",
            "+3 атаки против карт с защитой 3 и меньше.", a = 3, b = 3),
        card("y4", "Единорог", MYTHIC, 3, 4, "🦄", KILL_GAIN_ATK, "Жажда славы",
            "Убив карту, навсегда получает +1 атаки.", a = 1),
        card("y5", "Дриада", MYTHIC, 2, 5, "🌿", REFLECT, "Шипы",
            "Атаковавшая её карта получает 2 урона.", a = 2),
        card("y6", "Феникс", MYTHIC, 4, 3, "🔥", DEATH_DAMAGE_ALL, "Пепел",
            "Погибая, наносит 1 урон всем вражеским картам.", a = 1),
        card("y7", "Кентавр", MYTHIC, 3, 3, "🐎", PLAY_DAMAGE_RANDOM, "Налёт",
            "При выходе на поле наносит 2 урона случайной вражеской карте.", a = 2),
        card("y8", "Грифон", MYTHIC, 4, 3, "🦅", ATK_IF_FIELD_AT_MOST, "Свободный полёт",
            "+2 атаки, если на вашем поле не больше 3 карт.", a = 2, b = 3),
        card("y9", "Голем", MYTHIC, 2, 8, "🗿", TAUNT, "Каменный страж",
            "Враги обязаны атаковать эту карту первой."),
        card("y10", "Друид", MYTHIC, 2, 3, "🍃", ALLY_ATK_AURA, "Благословение рощи",
            "Другие ваши карты на поле получают +1 атаки.", a = 1),

        // ---------------- ЛЮДИ ----------------
        card("h1", "Воин", HUMANS, 3, 4, "🗡️", ATK_IF_ALONE, "Одиночный бой",
            "+3 атаки, если это единственная ваша карта на поле.", a = 3),
        card("h2", "Рабочий", HUMANS, 1, 4, "🔨", DEF_IF_FIELD_COUNT, "Стройка",
            "+3 защиты, если на вашем поле 5 карт.", a = 3, b = 5),
        card("h3", "Стрелок", HUMANS, 3, 2, "🏹", SWIFT, "Скорострел",
            "Ходит раньше всех остальных карт."),
        card("h4", "Рыцарь", HUMANS, 3, 5, "🛡️", ARMOR, "Латы",
            "Получаемый урон уменьшен на 1 (но не меньше 1).", a = 1),
        card("h5", "Паладин", HUMANS, 4, 4, "✨", ATK_WHEN_WOUNDED, "Праведный гнев",
            "+2 атаки, пока карта ранена.", a = 2),
        card("h6", "Маг", HUMANS, 2, 2, "🔮", PLAY_DAMAGE_RANDOM, "Огненный шар",
            "При выходе на поле наносит 3 урона случайной вражеской карте.", a = 3),
        card("h7", "Капитан", HUMANS, 3, 4, "🚩", PLAY_BUFF_ALLIES, "Боевой клич",
            "При выходе на поле ваши карты на поле навсегда получают +1 атаки.", a = 1),
        card("h8", "Лучница", HUMANS, 3, 3, "🎯", ATK_IF_FIELD_COUNT, "Прицельный огонь",
            "+2 атаки, если на вашем поле ровно 3 карты.", a = 2, b = 3),
        card("h9", "Алхимик", HUMANS, 2, 3, "⚗️", HIT_DRAIN_ATK, "Зелье слабости",
            "Ударенная карта навсегда теряет 2 атаки.", a = 2),
        card("h10", "Король", HUMANS, 2, 5, "👑", DEATH_BUFF_ALLIES, "Последний приказ",
            "Погибая, даёт союзникам на поле +3 атаки.", a = 3)
    )

    fun deck(f: Faction): List<CardTemplate> = all.filter { it.faction == f }
}

object BuffFactory {
    private val names = mapOf(
        (Stat.ATK to Stat.ATK) to "Кровавая охота",
        (Stat.ATK to Stat.DEF) to "Пробивной удар",
        (Stat.DEF to Stat.ATK) to "Железный бастион",
        (Stat.DEF to Stat.DEF) to "Тяжёлая осада"
    )

    /**
     * Три разных варианта. В каждом: бафф и дебафф себе, бафф и дебафф противнику.
     * Всё действует на все карты игрока (рука, поле, колода) до конца партии.
     */
    fun roll(rng: Random): List<BuffOption> {
        val combos = listOf(
            Stat.ATK to Stat.ATK, Stat.ATK to Stat.DEF,
            Stat.DEF to Stat.ATK, Stat.DEF to Stat.DEF
        ).shuffled(rng).take(3)
        return combos.mapIndexed { i, combo ->
            val selfStat = combo.first
            val foeStat = combo.second
            BuffOption(
                id = i,
                title = names.getValue(combo),
                selfBuff = Mod(selfStat, rng.nextInt(2, 4)),          // +2..+3
                selfDebuff = Mod(selfStat.other, -rng.nextInt(1, 3)), // −1..−2
                enemyBuff = Mod(foeStat.other, 1),                    // +1
                enemyDebuff = Mod(foeStat, -rng.nextInt(2, 4))        // −2..−3
            )
        }
    }

    fun botPick(options: List<BuffOption>, rng: Random): BuffOption {
        val best = options.maxOf { it.score }
        return options.filter { it.score == best }.random(rng)
    }
}
