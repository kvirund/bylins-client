package com.bylins.client.variables

/**
 * Источник переменной
 * Приоритет: SYSTEM > MSDP > STATUS > USER
 */
enum class VariableSource(val priority: Int, val prefix: String) {
    SYSTEM(0, "SYS"),      // Системные (host, port, connected) — высший приоритет
    MSDP(1, "MSDP"),       // Автоматически из MSDP
    STATUS(2, "STATUS"),   // Readonly отражение статуса (_status_*)
    USER(3, "USER")        // Пользовательские — только эти сохраняются
}

/**
 * Переменная с источником и типизированным значением
 */
data class Variable(
    val name: String,
    val value: Any,
    val source: VariableSource
) {
    /**
     * Строковое представление значения
     */
    fun asString(): String = when (value) {
        is String -> value
        is Map<*, *> -> formatMap(value)
        is List<*> -> formatList(value)
        else -> value.toString()
    }

    /**
     * Получает вложенное значение по пути (например, "exits.d")
     */
    fun getNestedValue(path: String): Any? {
        if (path.isEmpty()) return value

        val parts = path.split(".")
        var current: Any? = value

        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> {
                    // Пробуем и lowercase и uppercase ключи
                    current[part] ?: current[part.uppercase()] ?: current[part.lowercase()]
                }
                is List<*> -> {
                    val index = part.toIntOrNull()
                    if (index != null && index >= 0 && index < current.size) {
                        current[index]
                    } else null
                }
                else -> null
            }
            if (current == null) break
        }

        return current
    }

    companion object {
        fun formatMap(map: Map<*, *>): String {
            val entries = map.entries.joinToString(", ") { (k, v) ->
                "$k=${formatValue(v)}"
            }
            return "{$entries}"
        }

        fun formatList(list: List<*>): String {
            val items = list.joinToString(", ") { formatValue(it) }
            return "[$items]"
        }

        fun formatValue(value: Any?): String = when (value) {
            null -> "null"
            is Map<*, *> -> formatMap(value)
            is List<*> -> formatList(value)
            else -> value.toString()
        }
    }
}
