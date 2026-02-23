package com.bylins.client.assistant.perception

import mu.KotlinLogging

/**
 * Парсер вывода команды "афф" (аффекты персонажа).
 *
 * Формат вывода:
 * Аффекты: определение невидимости,определение магии,инфравидение,доблесть,летит,настороженность,мигание,регенерация новичка
 */
private val logger = KotlinLogging.logger("AffectsParser")

/**
 * Информация об аффектах персонажа.
 */
data class CharacterAffects(
    val affects: List<String>
) {
    fun isEmpty(): Boolean = affects.isEmpty()
}

class AffectsParser {

    // Callback при успешном парсинге
    var onAffectsParsed: ((CharacterAffects) -> Unit)? = null

    // Последний распарсенный результат
    private var lastAffects: CharacterAffects? = null

    // Regex для определения строки аффектов
    // Формат: "Аффекты: аффект1,аффект2,аффект3"
    private val affectsPattern = Regex("""^Аффекты:\s*(.*)$""", RegexOption.MULTILINE)

    // Альтернативный формат (если нет аффектов)
    private val noAffectsPattern = Regex("""На вас не действует ни одного заклинания""")

    /**
     * Попытаться распарсить блок текста как вывод "афф".
     * Возвращает CharacterAffects если текст содержит информацию об аффектах, null иначе.
     */
    fun tryParse(text: String): CharacterAffects? {
        // Проверяем нет ли сообщения об отсутствии аффектов
        if (noAffectsPattern.containsMatchIn(text)) {
            val affects = CharacterAffects(emptyList())
            lastAffects = affects
            onAffectsParsed?.invoke(affects)
            logger.debug { "Affects parsed: no affects" }
            return affects
        }

        // Ищем строку с аффектами
        val match = affectsPattern.find(text) ?: return null

        val affectsString = match.groupValues[1].trim()
        if (affectsString.isEmpty()) {
            val affects = CharacterAffects(emptyList())
            lastAffects = affects
            onAffectsParsed?.invoke(affects)
            return affects
        }

        // Разделяем по запятой и очищаем
        val affectsList = affectsString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val affects = CharacterAffects(affectsList)
        lastAffects = affects
        onAffectsParsed?.invoke(affects)

        logger.debug { "Affects parsed: ${affectsList.size} affects - ${affectsList.joinToString(", ")}" }
        return affects
    }

    /**
     * Получить последний распарсенный результат.
     */
    fun getLastAffects(): CharacterAffects? = lastAffects

    /**
     * Сбросить состояние.
     */
    fun reset() {
        lastAffects = null
    }
}
