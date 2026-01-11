package com.bylins.client.network

import kotlinx.serialization.json.*

/**
 * Парсер для GMCP (Generic MUD Communication Protocol) данных
 *
 * GMCP - это протокол для передачи структурированных данных между MUD сервером и клиентом.
 * Данные передаются в формате JSON.
 *
 * Формат сообщения: Package.SubPackage JSON_DATA
 * Пример: Char.Vitals {"hp": 100, "maxhp": 120}
 */
class GmcpParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Парсит GMCP сообщение
     *
     * @param data Сырые байты GMCP сообщения
     * @return Пара (пакет, JSON данные) или null если парсинг не удался
     */
    fun parse(data: ByteArray): GmcpMessage? {
        try {
            val message = String(data, Charsets.UTF_8).trim()

            if (message.isEmpty()) {
                return null
            }

            // GMCP формат: Package.SubPackage JSON_DATA
            // Ищем первый пробел, который разделяет пакет и данные
            val spaceIndex = message.indexOf(' ')

            if (spaceIndex == -1) {
                // Сообщение без данных (например, некоторые пакеты могут быть просто командами)
                return GmcpMessage(
                    packageName = message,
                    data = JsonObject(emptyMap())
                )
            }

            val packageName = message.substring(0, spaceIndex)
            val jsonString = message.substring(spaceIndex + 1).trim()

            // Парсим JSON данные
            val jsonData = try {
                json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                println("[GmcpParser] Failed to parse JSON: $jsonString")
                println("[GmcpParser] Error: ${e.message}")
                // Возвращаем пустой объект если не удалось распарсить
                JsonObject(emptyMap())
            }

            return GmcpMessage(
                packageName = packageName,
                data = jsonData
            )
        } catch (e: Exception) {
            println("[GmcpParser] Failed to parse GMCP message: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Извлекает значение из JSON по пути
     *
     * @param jsonElement JSON элемент
     * @param path Путь к значению (например, "player.hp")
     * @return Значение или null
     */
    fun getValueByPath(jsonElement: JsonElement, path: String): JsonElement? {
        val parts = path.split(".")
        var current = jsonElement

        for (part in parts) {
            current = when (current) {
                is JsonObject -> current[part] ?: return null
                is JsonArray -> {
                    val index = part.toIntOrNull()
                    if (index != null && index in 0 until current.size) {
                        current[index]
                    } else {
                        return null
                    }
                }
                else -> return null
            }
        }

        return current
    }

    /**
     * Преобразует JsonElement в строку для отображения
     */
    fun jsonToString(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonNull -> "null"
            else -> element.toString()
        }
    }

    /**
     * Преобразует JsonElement в Map<String, Any> для удобного доступа
     */
    fun jsonToMap(element: JsonElement): Map<String, Any?>? {
        if (element !is JsonObject) return null

        val map = mutableMapOf<String, Any?>()

        for ((key, value) in element) {
            map[key] = when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.intOrNull != null -> value.int
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is JsonNull -> null
                is JsonObject -> jsonToMap(value)
                is JsonArray -> value.map {
                    when (it) {
                        is JsonPrimitive -> it.content
                        is JsonObject -> jsonToMap(it)
                        else -> it.toString()
                    }
                }
            }
        }

        return map
    }
}

/**
 * GMCP сообщение
 *
 * @param packageName Название пакета (например, "Char.Vitals", "Room.Info")
 * @param data JSON данные
 */
data class GmcpMessage(
    val packageName: String,
    val data: JsonElement
)
