package com.bylins.client.bot.perception

import mu.KotlinLogging

/**
 * Парсер вывода команды "сч" и "сч все".
 *
 * Применяет regex к блокам текста и извлекает данные персонажа.
 * Работает безусловно - если regex совпал, извлекаем данные.
 */
private val logger = KotlinLogging.logger("ScoreParser")

data class CharacterScore(
    // Основная информация
    val name: String? = null,
    val race: String? = null,       // Русич
    val tribe: String? = null,      // Полянин
    val religion: String? = null,   // Язычник
    val className: String? = null,  // наемник
    val level: Int? = null,
    val remorts: Int? = null,       // Перевоплощений
    val age: Int? = null,

    // Жизненные показатели
    val hp: Int? = null,
    val maxHp: Int? = null,
    val move: Int? = null,
    val maxMove: Int? = null,
    val mana: Int? = null,          // Магическая энергия
    val maxMana: Int? = null,
    val manaRegen: Int? = null,     // Восстановление маны в сек
    val hpRegen: Int? = null,       // Восст. жизни %
    val hpRegenBase: Int? = null,   // Базовое восстановление жизни
    val moveRegen: Int? = null,     // Восст. сил %
    val moveRegenBase: Int? = null, // Базовое восстановление сил

    // Опыт и деньги
    val experience: Long? = null,
    val expToLevel: Long? = null,   // ДСУ (до следующего уровня)
    val gold: Int? = null,          // В кармане
    val bank: Long? = null,         // В банке/лежне

    // Характеристики (текущее/базовое)
    val str: Int? = null, val strBase: Int? = null,
    val dex: Int? = null, val dexBase: Int? = null,
    val con: Int? = null, val conBase: Int? = null,
    val wis: Int? = null, val wisBase: Int? = null,
    val int: Int? = null, val intBase: Int? = null,
    val cha: Int? = null, val chaBase: Int? = null,

    // Размеры
    val height: Int? = null, val heightBase: Int? = null,
    val weight: Int? = null, val weightBase: Int? = null,
    val size: Int? = null, val sizeBase: Int? = null,

    // Боевые характеристики
    val ac: Int? = null,            // Защита (AC)
    val armor: Int? = null,         // Броня
    val absorb: Int? = null,        // Поглощение
    val hitroll: Int? = null,       // Попадание
    val damroll: Int? = null,       // Повреждение
    val castLevel: Int? = null,     // Колдовство
    val memorySlots: Int? = null,   // Запоминание
    val luck: Int? = null,          // Удача
    val spellPower: Int? = null,    // Сила заклинаний %
    val physDamage: Int? = null,    // Физ. урон %
    val initiative: Int? = null,    // Инициатива

    // Спас-броски
    val saveWill: Int? = null,      // Воля
    val saveHealth: Int? = null,    // Здоровье
    val saveStability: Int? = null, // Стойкость
    val saveReflex: Int? = null,    // Реакция

    // Сопротивления
    val resistDamage: Int? = null,      // Урону
    val resistSpells: Int? = null,      // Заклинаниям
    val resistFire: Int? = null,        // Магии огня
    val resistWater: Int? = null,       // Магии воды
    val resistEarth: Int? = null,       // Магии земли
    val resistAir: Int? = null,         // Магии воздуха
    val resistDark: Int? = null,        // Магии тьмы
    val resistMind: Int? = null,        // Магии разума
    val resistWounds: Int? = null,      // Тяжелым ранам
    val resistPoison: Int? = null,      // Ядам и болезням

    // Слава и прочее
    val glory: Int? = null,             // Очки славы
    val groupLevelDiff: Int? = null,    // Разница в уровнях для группы
    val maxGroupSize: Int? = null,      // Макс. соратников

    // Состояние
    val position: String? = null,       // стоите, сидите, etc
    val isHungry: Boolean = false,
    val isThirsty: Boolean = false,
    val isSafe: Boolean = false
)

class ScoreParser {

    private var lastScore: CharacterScore? = null

    // Callback при успешном парсинге
    var onScoreParsed: ((CharacterScore) -> Unit)? = null

    // Детектор типа вывода
    private val fullScoreMarker = Regex("""\+---""")
    // Имя может содержать пробелы: "Вечно весёлый Хмель"
    private val briefScoreMarker = Regex("""Вы\s+.+?\s+\([^)]+уровня\)""")

    /**
     * Попытаться распарсить блок текста как вывод "сч".
     * Возвращает CharacterScore если текст похож на вывод счёта, null иначе.
     */
    fun tryParse(text: String): CharacterScore? {
        // Определяем тип вывода
        val isFullScore = fullScoreMarker.containsMatchIn(text)
        val isBriefScore = briefScoreMarker.containsMatchIn(text)

        if (!isFullScore && !isBriefScore) {
            return null
        }

        val score = if (isFullScore) {
            parseFullScore(text)
        } else {
            parseBriefScore(text)
        }

        // Проверяем, что хоть что-то распарсилось
        if (score.level == null && score.hp == null && score.str == null) {
            return null
        }

        lastScore = score
        onScoreParsed?.invoke(score)

        logger.debug { "Score parsed: level=${score.level}, hp=${score.hp}/${score.maxHp}" }
        return score
    }

    /**
     * Получить последний распарсенный счёт.
     */
    fun getLastScore(): CharacterScore? = lastScore

    /**
     * Парсит краткий вывод "сч".
     */
    private fun parseBriefScore(text: String): CharacterScore {
        var score = CharacterScore()

        // Вы Зломысл (Русич, Полянин, Язычник, наемник 28 уровня).
        // Вы Вечно весёлый Хмель (Русич, Древлянин, Язычник, волхв 34 уровня).
        // Имя может содержать пробелы!
        Regex("""Вы\s+(.+?)\s+\(([^,]+),\s*([^,]+),\s*([^,]+),\s*(\S+)\s+(\d+)\s+уровня\)""")
            .find(text)?.let { m ->
                score = score.copy(
                    name = m.groupValues[1],
                    race = m.groupValues[2],
                    tribe = m.groupValues[3],
                    religion = m.groupValues[4],
                    className = m.groupValues[5],
                    level = m.groupValues[6].toIntOrNull()
                )
            }

        // Сейчас вам 324 года
        Regex("""вам\s+(\d+)\s+(?:год|года|лет)""").find(text)?.let { m ->
            score = score.copy(age = m.groupValues[1].toIntOrNull())
        }

        // выдержать 478(478) единиц повреждения, и пройти 258(258) верст
        Regex("""выдержать\s+(\d+)\((\d+)\).*пройти\s+(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(
                hp = m.groupValues[1].toIntOrNull(),
                maxHp = m.groupValues[2].toIntOrNull(),
                move = m.groupValues[3].toIntOrNull(),
                maxMove = m.groupValues[4].toIntOrNull()
            )
        }

        // Магическая энергия для иммов: Ваша магическая энергия 4400(4400) и вы восстанавливаете 19 в сек.
        Regex("""магическая энергия\s+(\d+)\((\d+)\)""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            score = score.copy(
                mana = m.groupValues[1].toIntOrNull(),
                maxMana = m.groupValues[2].toIntOrNull()
            )
        }
        // Восстановление маны в сек
        Regex("""восстанавливаете\s+(\d+)\s+в\s+сек""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            score = score.copy(manaRegen = m.groupValues[1].toIntOrNull())
        }

        // Характеристики
        Regex("""Сила\s*:\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(str = m.groupValues[1].toIntOrNull(), strBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Подв\s*:\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(dex = m.groupValues[1].toIntOrNull(), dexBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Тело\s*:\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(con = m.groupValues[1].toIntOrNull(), conBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Мудр\s*:\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(wis = m.groupValues[1].toIntOrNull(), wisBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Ум\s*:\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(int = m.groupValues[1].toIntOrNull(), intBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Обаян\s*:\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(cha = m.groupValues[1].toIntOrNull(), chaBase = m.groupValues[2].toIntOrNull())
        }

        // Размеры
        Regex("""Размер\s+(\d+)\(\s*(\d+)\)""").find(text)?.let { m ->
            score = score.copy(size = m.groupValues[1].toIntOrNull(), sizeBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Рост\s+(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(height = m.groupValues[1].toIntOrNull(), heightBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Вес\s+(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(weight = m.groupValues[1].toIntOrNull(), weightBase = m.groupValues[2].toIntOrNull())
        }

        // Защита  (AC)     :    5
        Regex("""Защита\s+\(AC\)\s*:\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(ac = m.groupValues[1].toIntOrNull())
        }

        // Формат для иммов: AC   :  100(  50)  DR   :    0(  10)
        // AC с базовым значением в скобках
        Regex("""AC\s*:\s*(-?\d+)\s*\(\s*(-?\d+)\s*\)""").find(text)?.let { m ->
            score = score.copy(ac = m.groupValues[1].toIntOrNull())
            // Базовое AC в m.groupValues[2], но мы его не храним
        }

        // DR (damroll) для иммов: DR   :    0(  10)
        Regex("""DR\s*:\s*(-?\d+)\s*\(\s*(-?\d+)\s*\)""").find(text)?.let { m ->
            score = score.copy(damroll = m.groupValues[1].toIntOrNull())
            // Базовое DR в m.groupValues[2]
        }

        // Броня/Поглощение :   17/0
        Regex("""Броня/Поглощение\s*:\s*(\d+)/(\d+)""").find(text)?.let { m ->
            score = score.copy(
                armor = m.groupValues[1].toIntOrNull(),
                absorb = m.groupValues[2].toIntOrNull()
            )
        }

        // Опыт и остаток
        Regex("""опыт\s*-\s*([\d\s]+)\s*очк""").find(text)?.let { m ->
            score = score.copy(experience = m.groupValues[1].replace(Regex("\\s"), "").toLongOrNull())
        }
        Regex("""осталось набрать\s+([\d\s]+)\s*очк""").find(text)?.let { m ->
            score = score.copy(expToLevel = m.groupValues[1].replace(Regex("\\s"), "").toLongOrNull())
        }

        // Деньги
        Regex("""на руках\s+([\d\s]+)\s*кун""").find(text)?.let { m ->
            score = score.copy(gold = m.groupValues[1].replace(Regex("\\s"), "").toIntOrNull())
        }
        Regex("""припрятано.*?([\d\s]+)\s*кун""").find(text)?.let { m ->
            score = score.copy(bank = m.groupValues[1].replace(Regex("\\s"), "").toLongOrNull())
        }

        // Группа
        Regex("""разницей\s+(?:в\s+)?(\d+)\s+уровн""").find(text)?.let { m ->
            score = score.copy(groupLevelDiff = m.groupValues[1].toIntOrNull())
        }

        // Слава
        Regex("""заслужили\s+([\d\s]+)\s*очк""").find(text)?.let { m ->
            score = score.copy(glory = m.groupValues[1].replace(Regex("\\s"), "").toIntOrNull())
        }

        // Состояние
        score = score.copy(
            position = when {
                text.contains("Вы стоите") -> "стоите"
                text.contains("Вы сидите") -> "сидите"
                text.contains("Вы отдыхаете") -> "отдыхаете"
                text.contains("Вы спите") -> "спите"
                text.contains("Вы сражаетесь") -> "сражаетесь"
                else -> null
            },
            isHungry = text.contains("голодн", ignoreCase = true),
            isThirsty = text.contains("жажд", ignoreCase = true),
            isSafe = text.contains("в безопасности")
        )

        return score
    }

    /**
     * Парсит полный вывод "сч все" (таблица).
     */
    private fun parseFullScore(text: String): CharacterScore {
        var score = CharacterScore()

        // Вы Зломысл, наемник.
        Regex("""Вы\s+(\S+),\s*(\S+)\.""").find(text)?.let { m ->
            score = score.copy(name = m.groupValues[1], className = m.groupValues[2])
        }

        // Из таблицы
        Regex("""Племя:\s*(\S+)""").find(text)?.let { m ->
            score = score.copy(tribe = m.groupValues[1])
        }
        Regex("""Вера:\s*(\S+)""").find(text)?.let { m ->
            score = score.copy(religion = m.groupValues[1])
        }
        Regex("""Уровень:\s*(\d+)""").find(text)?.let { m ->
            score = score.copy(level = m.groupValues[1].toIntOrNull())
        }
        Regex("""Перевоплощений:\s*(\d+)""").find(text)?.let { m ->
            score = score.copy(remorts = m.groupValues[1].toIntOrNull())
        }
        Regex("""Возраст:\s*(\d+)""").find(text)?.let { m ->
            score = score.copy(age = m.groupValues[1].toIntOrNull())
        }

        // Опыт в таблице (с пробелами): Опыт: 34 598 251
        Regex("""Опыт:\s*([\d\s]+)""").find(text)?.let { m ->
            score = score.copy(experience = m.groupValues[1].replace(Regex("\\s"), "").toLongOrNull())
        }
        Regex("""ДСУ:\s*([\d\s]+)""").find(text)?.let { m ->
            score = score.copy(expToLevel = m.groupValues[1].replace(Regex("\\s"), "").toLongOrNull())
        }
        Regex("""Кун:\s*([\d\s]+)""").find(text)?.let { m ->
            score = score.copy(gold = m.groupValues[1].replace(Regex("\\s"), "").toIntOrNull())
        }
        Regex("""На счету:\s*([\d\s]+)""").find(text)?.let { m ->
            score = score.copy(bank = m.groupValues[1].replace(Regex("\\s"), "").toLongOrNull())
        }

        // Характеристики: | Сила         |    23 (23) |
        Regex("""Сила\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(str = m.groupValues[1].toIntOrNull(), strBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Ловкость\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(dex = m.groupValues[1].toIntOrNull(), dexBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Телосложение\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(con = m.groupValues[1].toIntOrNull(), conBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Мудрость\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(wis = m.groupValues[1].toIntOrNull(), wisBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Интеллект\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(int = m.groupValues[1].toIntOrNull(), intBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Обаяние\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(cha = m.groupValues[1].toIntOrNull(), chaBase = m.groupValues[2].toIntOrNull())
        }

        // Размеры
        Regex("""Рост\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(height = m.groupValues[1].toIntOrNull(), heightBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Вес\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(weight = m.groupValues[1].toIntOrNull(), weightBase = m.groupValues[2].toIntOrNull())
        }
        Regex("""Размер\s*\|\s*(\d+)\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(size = m.groupValues[1].toIntOrNull(), sizeBase = m.groupValues[2].toIntOrNull())
        }

        // Жизнь и выносливость
        Regex("""Жизнь\s*\|\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(hp = m.groupValues[1].toIntOrNull(), maxHp = m.groupValues[2].toIntOrNull())
        }
        Regex("""Выносливость\s*\|\s*(\d+)\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(move = m.groupValues[1].toIntOrNull(), maxMove = m.groupValues[2].toIntOrNull())
        }
        // Восст. жизни | +60% (85) - процент и базовое значение
        Regex("""Восст\.\s*жизни\s*\|\s*\+?(\d+)%\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(
                hpRegen = m.groupValues[1].toIntOrNull(),
                hpRegenBase = m.groupValues[2].toIntOrNull()
            )
        }
        // Восст. сил | +215% (53)
        Regex("""Восст\.\s*сил\s*\|\s*\+?(\d+)%\s*\((\d+)\)""").find(text)?.let { m ->
            score = score.copy(
                moveRegen = m.groupValues[1].toIntOrNull(),
                moveRegenBase = m.groupValues[2].toIntOrNull()
            )
        }

        // Боевые характеристики
        Regex("""Попадание\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(hitroll = m.groupValues[1].toIntOrNull())
        }
        Regex("""Повреждение\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(damroll = m.groupValues[1].toIntOrNull())
        }
        Regex("""Колдовство\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(castLevel = m.groupValues[1].toIntOrNull())
        }
        Regex("""Запоминание\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(memorySlots = m.groupValues[1].toIntOrNull())
        }
        Regex("""Удача\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(luck = m.groupValues[1].toIntOrNull())
        }
        Regex("""Сила заклинаний\s*%\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(spellPower = m.groupValues[1].toIntOrNull())
        }
        Regex("""Физ\.\s*урон\s*%\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(physDamage = m.groupValues[1].toIntOrNull())
        }
        Regex("""Инициатива\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(initiative = m.groupValues[1].toIntOrNull())
        }

        // Броня и защита
        Regex("""Броня\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(armor = m.groupValues[1].toIntOrNull())
        }
        Regex("""Защита\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(ac = m.groupValues[1].toIntOrNull())
        }
        Regex("""Поглощение\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(absorb = m.groupValues[1].toIntOrNull())
        }

        // Спас-броски
        Regex("""Воля\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(saveWill = m.groupValues[1].toIntOrNull())
        }
        Regex("""Здоровье\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(saveHealth = m.groupValues[1].toIntOrNull())
        }
        Regex("""Стойкость\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(saveStability = m.groupValues[1].toIntOrNull())
        }
        Regex("""Реакция\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(saveReflex = m.groupValues[1].toIntOrNull())
        }

        // Сопротивления
        Regex("""Урону\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistDamage = m.groupValues[1].toIntOrNull())
        }
        Regex("""Заклинаниям\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistSpells = m.groupValues[1].toIntOrNull())
        }
        Regex("""Магии огня\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistFire = m.groupValues[1].toIntOrNull())
        }
        Regex("""Магии воды\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistWater = m.groupValues[1].toIntOrNull())
        }
        Regex("""Магии земли\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistEarth = m.groupValues[1].toIntOrNull())
        }
        Regex("""Магии воздуха\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistAir = m.groupValues[1].toIntOrNull())
        }
        Regex("""Магии тьмы\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistDark = m.groupValues[1].toIntOrNull())
        }
        Regex("""Магии разума\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistMind = m.groupValues[1].toIntOrNull())
        }
        Regex("""Тяжелым ранам\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistWounds = m.groupValues[1].toIntOrNull())
        }
        Regex("""Ядам и болезням\s*\|\s*(-?\d+)""").find(text)?.let { m ->
            score = score.copy(resistPoison = m.groupValues[1].toIntOrNull())
        }

        // Группа
        Regex("""разницей\s+(\d+)\s+уровн""").find(text)?.let { m ->
            score = score.copy(groupLevelDiff = m.groupValues[1].toIntOrNull())
        }
        Regex("""максимум\s+(\d+)\s+соратник""").find(text)?.let { m ->
            score = score.copy(maxGroupSize = m.groupValues[1].toIntOrNull())
        }

        // Слава
        Regex("""заслужили\s+([\d\s]+)\s*очк""").find(text)?.let { m ->
            score = score.copy(glory = m.groupValues[1].replace(Regex("\\s"), "").toIntOrNull())
        }

        // Состояние
        score = score.copy(
            position = when {
                text.contains("Вы стоите") -> "стоите"
                text.contains("Вы сидите") -> "сидите"
                text.contains("Вы отдыхаете") -> "отдыхаете"
                text.contains("Вы спите") -> "спите"
                text.contains("Вы сражаетесь") -> "сражаетесь"
                else -> null
            },
            isHungry = text.contains("Голоден") || text.contains("Угу"),
            isThirsty = text.contains("Жажда") || text.contains("Наливай"),
            isSafe = text.contains("в безопасности")
        )

        return score
    }
}
