package com.bylins.client.aicontrol

import kotlinx.serialization.json.*

/**
 * Мост между «сырыми» структурами клиента (Map/List/примитивы, как их отдаёт
 * ClientControl) и JSON. Отдельный слой нужен, чтобы не навешивать
 * @Serializable на типы, которых плагин не контролирует.
 */
object ApiJson {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    /** Превращает произвольное значение клиента в JsonElement. */
    fun encode(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), encode(v)) }
        }
        is Iterable<*> -> buildJsonArray { value.forEach { add(encode(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    fun stringify(value: Any?): String = json.encodeToString(JsonElement.serializer(), encode(value))

    /** Разбирает тело запроса; пустое тело — пустой объект. */
    fun parseObject(body: String): JsonObject =
        if (body.isBlank()) JsonObject(emptyMap())
        else json.parseToJsonElement(body).jsonObject

    // --- Удобные чтения полей запроса ---

    fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
    fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default
    fun JsonObject.long(key: String, default: Long): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: default

    fun JsonObject.strList(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    /** Значения для передачи в ClientControl.update*(changes) — как обычные типы Kotlin. */
    fun JsonObject.toChanges(): Map<String, Any?> = mapValues { (_, v) -> v.toKotlin() }

    private fun JsonElement.toKotlin(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> boolean
            longOrNull != null -> long
            doubleOrNull != null -> double
            else -> content
        }
        is JsonArray -> map { it.toKotlin() }
        is JsonObject -> mapValues { (_, v) -> v.toKotlin() }
    }
}
