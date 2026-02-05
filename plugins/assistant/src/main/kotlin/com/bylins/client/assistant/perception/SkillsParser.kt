package com.bylins.client.assistant.perception

import mu.KotlinLogging

/**
 * Парсер вывода команды "ум" (умения персонажа).
 *
 * Формат вывода:
 * Вы владеете следующими умениями:
 * [-!-] опознание                (ниже среднего) 33 (53)
 *       прислушаться             (плохо) 21 (53)
 * [-!-] опохмелиться             (очень плохо) 13 (53)
 * [XXX] некое умение             (хорошо) 70 (100)
 *
 * Формат строки:
 * [XXX] skill_name               (level_desc) current (max)
 *
 * - [-!-] = умение готово к использованию (нет отката)
 * - [XXX] = откат в часах (XXX = число)
 * - "      " (пробелы) = умение без механики отката
 */
private val logger = KotlinLogging.logger("SkillsParser")

/**
 * Запись об одном умении.
 */
data class SkillEntry(
    val name: String,           // "опознание"
    val levelDesc: String,      // "ниже среднего"
    val current: Int,           // 33
    val max: Int,               // 53
    val cooldown: Int?,         // null = нет механики отката, 0 = ready [-!-], >0 = часов отката
    val hasCooldown: Boolean    // true если умение имеет откат (даже если сейчас готово)
) {
    /**
     * Готово ли умение к использованию.
     * - Если hasCooldown=false (нет механики отката) -> всегда готово
     * - Если hasCooldown=true и cooldown=0 -> готово ([-!-])
     * - Если hasCooldown=true и cooldown>0 -> на откате
     */
    fun isReady(): Boolean = !hasCooldown || cooldown == 0
}

/**
 * Информация об умениях персонажа.
 */
data class CharacterSkills(
    val skills: List<SkillEntry>
) {
    fun isEmpty(): Boolean = skills.isEmpty()

    /**
     * Получить умение по имени (case-insensitive).
     */
    fun getSkill(name: String): SkillEntry? =
        skills.find { it.name.equals(name, ignoreCase = true) }

    /**
     * Получить все умения с откатом.
     */
    fun getSkillsWithCooldown(): List<SkillEntry> =
        skills.filter { it.hasCooldown }

    /**
     * Получить все умения, готовые к использованию.
     */
    fun getReadySkills(): List<SkillEntry> =
        skills.filter { it.isReady() }

    /**
     * Получить все умения на откате.
     */
    fun getSkillsOnCooldown(): List<SkillEntry> =
        skills.filter { it.hasCooldown && (it.cooldown ?: 0) > 0 }
}

class SkillsParser {

    // Callback при успешном парсинге
    var onSkillsParsed: ((CharacterSkills) -> Unit)? = null

    // Последний распарсенный результат
    private var lastSkills: CharacterSkills? = null

    // Маркер начала вывода команды "ум"
    private val skillsHeaderPattern = Regex("""Вы владеете следующими умениями:""")

    // Regex для парсинга строки умения
    // Формат: "[-!-] опознание                (ниже среднего) 33 (53)"
    //         "[  3] боевой клич              (хорошо) 70 (100)"
    //         "      прислушаться             (плохо) 21 (53)"
    // Группы: 1 = cooldown indicator (или пустая строка для умений без отката),
    //         2 = skill name, 3 = level desc, 4 = current, 5 = max
    // Для умений без отката: любое количество пробелов в начале (без скобок)
    private val skillLinePattern = Regex(
        """^(\[-!-\]|\[\s*\d+\]|)\s+(\S+(?:\s+\S+)*?)\s+\(([^)]+)\)\s+(\d+)\s+\((\d+)\)\s*$"""
    )

    // Альтернативный формат (нет умений)
    private val noSkillsPattern = Regex("""Боги вас обделили умениями""")

    /**
     * Попытаться распарсить блок текста как вывод "ум".
     * Возвращает CharacterSkills если текст содержит информацию об умениях, null иначе.
     */
    fun tryParse(text: String): CharacterSkills? {
        // Проверяем заголовок
        if (!skillsHeaderPattern.containsMatchIn(text)) {
            // Проверяем альтернативный формат
            if (noSkillsPattern.containsMatchIn(text)) {
                val skills = CharacterSkills(emptyList())
                lastSkills = skills
                onSkillsParsed?.invoke(skills)
                logger.debug { "Skills parsed: no skills" }
                return skills
            }
            return null
        }

        val skillsList = mutableListOf<SkillEntry>()

        // Парсим каждую строку
        for (line in text.lines()) {
            val match = skillLinePattern.find(line) ?: continue

            val cooldownIndicator = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val levelDesc = match.groupValues[3].trim()
            val current = match.groupValues[4].toIntOrNull() ?: continue
            val max = match.groupValues[5].toIntOrNull() ?: continue

            // Определяем cooldown
            val (cooldown, hasCooldown) = when {
                cooldownIndicator == "[-!-]" -> Pair(0, true)  // Готово (с механикой отката)
                cooldownIndicator.startsWith("[") && cooldownIndicator.endsWith("]") -> {
                    // Извлекаем число из [  3] или [12]
                    val hours = cooldownIndicator
                        .removePrefix("[")
                        .removeSuffix("]")
                        .trim()
                        .toIntOrNull() ?: 0
                    Pair(hours, true)  // На откате
                }
                else -> Pair(null, false)  // Без механики отката (пустая строка или пробелы)
            }

            skillsList.add(
                SkillEntry(
                    name = name,
                    levelDesc = levelDesc,
                    current = current,
                    max = max,
                    cooldown = cooldown,
                    hasCooldown = hasCooldown
                )
            )
        }

        if (skillsList.isEmpty()) {
            return null
        }

        val skills = CharacterSkills(skillsList)
        lastSkills = skills
        onSkillsParsed?.invoke(skills)

        logger.debug { "Skills parsed: ${skillsList.size} skills" }
        return skills
    }

    /**
     * Получить последний распарсенный результат.
     */
    fun getLastSkills(): CharacterSkills? = lastSkills

    /**
     * Сбросить состояние.
     */
    fun reset() {
        lastSkills = null
    }
}
