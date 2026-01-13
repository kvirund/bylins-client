package com.bylins.client.variables

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Управляет переменными с поддержкой источников и приоритетов
 *
 * Переменные можно использовать в триггерах, алиасах и хоткеях
 * Синтаксис: @varname или ${varname}
 *
 * Источники (приоритет):
 * - SYSTEM: системные переменные (host, port, connected)
 * - MSDP: автоматически из MSDP протокола
 * - STATUS: readonly отражение статус-панели
 * - USER: пользовательские (только они сохраняются)
 */
class VariableManager {
    private val _variables = MutableStateFlow<Map<String, Variable>>(emptyMap())
    val variables: StateFlow<Map<String, Variable>> = _variables

    /**
     * Устанавливает переменную с указанным источником
     * Учитывает приоритеты: переменная с более высоким приоритетом не перезаписывается
     */
    fun setVariable(name: String, value: Any, source: VariableSource = VariableSource.USER): Boolean {
        val existing = _variables.value[name]

        // Если переменная существует с более высоким приоритетом - не перезаписываем
        if (existing != null && existing.source.priority < source.priority) {
            return false
        }

        _variables.value = _variables.value + (name to Variable(name, value, source))
        return true
    }

    /**
     * Устанавливает переменную (для совместимости со старым API)
     */
    fun setVariable(name: String, value: String) {
        setVariable(name, value as Any, VariableSource.USER)
    }

    /**
     * Устанавливает системную переменную
     */
    fun setSystemVariable(name: String, value: Any) {
        setVariable(name, value, VariableSource.SYSTEM)
    }

    /**
     * Устанавливает MSDP переменную
     */
    fun setMsdpVariable(name: String, value: Any) {
        setVariable(name, value, VariableSource.MSDP)
    }

    /**
     * Устанавливает STATUS переменную (readonly отражение статуса)
     */
    fun setStatusVariable(name: String, value: Any) {
        setVariable(name, value, VariableSource.STATUS)
    }

    /**
     * Получает переменную
     */
    fun getVariable(name: String): Variable? {
        return _variables.value[name]
    }

    /**
     * Получает строковое значение переменной
     */
    fun getVariableValue(name: String): String? {
        return _variables.value[name]?.asString()
    }

    /**
     * Получает вложенное значение по пути (например, "room.exits.d")
     */
    fun getNestedValue(path: String): Any? {
        val parts = path.split(".", limit = 2)
        val varName = parts[0]
        val nestedPath = if (parts.size > 1) parts[1] else ""

        val variable = _variables.value[varName] ?: return null
        return if (nestedPath.isEmpty()) {
            variable.value
        } else {
            variable.getNestedValue(nestedPath)
        }
    }

    /**
     * Удаляет переменную
     * Можно удалить только USER переменные
     */
    fun removeVariable(name: String): Boolean {
        val existing = _variables.value[name]
        if (existing == null) return false
        if (existing.source != VariableSource.USER) return false

        _variables.value = _variables.value - name
        return true
    }

    /**
     * Удаляет переменную по источнику (для внутреннего использования)
     */
    fun removeVariableBySource(name: String, source: VariableSource) {
        val existing = _variables.value[name]
        if (existing?.source == source) {
            _variables.value = _variables.value - name
        }
    }

    /**
     * Очищает все переменные указанного источника
     */
    fun clearBySource(source: VariableSource) {
        _variables.value = _variables.value.filterValues { it.source != source }
    }

    /**
     * Очищает все пользовательские переменные
     */
    fun clear() {
        clearBySource(VariableSource.USER)
    }

    /**
     * Заменяет переменные в тексте
     * Поддерживает синтаксис @varname и ${varname}
     * Также поддерживает вложенный доступ: ${room.exits.d}
     */
    fun substituteVariables(text: String): String {
        var result = text

        // Заменяем ${varname} или ${varname.nested.path}
        val bracePattern = """\$\{([a-zA-Z_][a-zA-Z0-9_.]*)\}""".toRegex()
        result = bracePattern.replace(result) { matchResult ->
            val path = matchResult.groupValues[1]
            val value = getNestedValue(path)
            if (value != null) Variable.formatValue(value) else matchResult.value
        }

        // Заменяем @varname (только простые имена, без вложенности)
        val atPattern = """@([a-zA-Z_][a-zA-Z0-9_]*)(?![a-zA-Z0-9_])""".toRegex()
        result = atPattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            getVariableValue(varName) ?: matchResult.value
        }

        return result
    }

    /**
     * Обрабатывает команды управления переменными
     * #var name value - установить переменную
     * #unvar name - удалить переменную
     * #vars - показать все переменные
     * #vars name - показать конкретную переменную
     * #vars name.path - показать вложенное значение
     */
    fun processCommand(command: String, outputCallback: (String) -> Unit): Boolean {
        val trimmed = command.trim()

        // #var name value - установить переменную
        if (trimmed.startsWith("#var ")) {
            val parts = trimmed.substring(5).split(" ", limit = 2)
            if (parts.size >= 2) {
                val name = parts[0]
                val value = parts[1]

                // Проверяем, не занято ли имя переменной с более высоким приоритетом
                val existing = _variables.value[name]
                if (existing != null && existing.source != VariableSource.USER) {
                    outputCallback("\u001B[1;31m[Ошибка: переменная '$name' защищена (${existing.source.prefix})]\u001B[0m")
                    return true
                }

                setVariable(name, value)
                outputCallback("\u001B[1;32m[Переменная установлена: $name = $value]\u001B[0m")
                return true
            } else if (parts.size == 1) {
                // Показать значение переменной
                val name = parts[0]
                val variable = getVariable(name)
                if (variable != null) {
                    outputCallback("\u001B[1;36m[${variable.source.prefix}] $name = ${variable.asString()}\u001B[0m")
                } else {
                    outputCallback("\u001B[1;31m[Переменная не найдена: $name]\u001B[0m")
                }
                return true
            }
        }

        // #unvar name - удалить переменную
        if (trimmed.startsWith("#unvar ")) {
            val name = trimmed.substring(7).trim()
            if (name.isNotEmpty()) {
                val existing = _variables.value[name]
                if (existing == null) {
                    outputCallback("\u001B[1;31m[Переменная не найдена: $name]\u001B[0m")
                } else if (existing.source != VariableSource.USER) {
                    outputCallback("\u001B[1;31m[Ошибка: переменная '$name' защищена (${existing.source.prefix})]\u001B[0m")
                } else {
                    removeVariable(name)
                    outputCallback("\u001B[1;33m[Переменная удалена: $name]\u001B[0m")
                }
                return true
            }
        }

        // #vars name=value - установить переменную (альтернативный синтаксис)
        if (trimmed.startsWith("#vars ") && trimmed.contains("=")) {
            val assignment = trimmed.substring(6).trim()
            val eqIndex = assignment.indexOf('=')
            if (eqIndex > 0) {
                val name = assignment.substring(0, eqIndex).trim()
                val value = assignment.substring(eqIndex + 1).trim()

                val existing = _variables.value[name]
                if (existing != null && existing.source != VariableSource.USER) {
                    outputCallback("\u001B[1;31m[Ошибка: переменная '$name' защищена (${existing.source.prefix})]\u001B[0m")
                    return true
                }

                setVariable(name, value)
                outputCallback("\u001B[1;32m[Переменная установлена: $name = $value]\u001B[0m")
                return true
            }
        }

        // #vars path - показать конкретную переменную или вложенное значение
        if (trimmed.startsWith("#vars ") && !trimmed.contains("=")) {
            val path = trimmed.substring(6).trim()
            if (path.isNotEmpty()) {
                val parts = path.split(".", limit = 2)
                val varName = parts[0]
                val variable = getVariable(varName)

                if (variable == null) {
                    outputCallback("\u001B[1;31m[Переменная не найдена: $varName]\u001B[0m")
                } else if (parts.size == 1) {
                    // Показываем всю переменную
                    outputCallback("\u001B[1;36m[${variable.source.prefix}] $varName = ${variable.asString()}\u001B[0m")
                } else {
                    // Показываем вложенное значение
                    val nestedPath = parts[1]
                    val nestedValue = variable.getNestedValue(nestedPath)
                    if (nestedValue != null) {
                        outputCallback("\u001B[1;36m$path = ${Variable.formatValue(nestedValue)}\u001B[0m")
                    } else {
                        outputCallback("\u001B[1;31m[Путь не найден: $path]\u001B[0m")
                    }
                }
                return true
            }
        }

        // #vars - показать все переменные
        if (trimmed == "#vars") {
            val vars = _variables.value
            if (vars.isEmpty()) {
                outputCallback("\u001B[1;33m[Переменные не установлены]\u001B[0m")
            } else {
                val sb = StringBuilder()
                sb.append("\u001B[1;36m[Переменные (${vars.size}):]\u001B[0m")

                // Группируем по источнику
                val grouped = vars.values.groupBy { it.source }

                VariableSource.entries.forEach { source ->
                    val sourceVars = grouped[source] ?: return@forEach
                    if (sourceVars.isNotEmpty()) {
                        sourceVars.sortedBy { it.name }.forEach { variable ->
                            val colorCode = when (source) {
                                VariableSource.SYSTEM -> "1;35"  // Magenta
                                VariableSource.MSDP -> "1;33"    // Yellow
                                VariableSource.STATUS -> "1;34"  // Blue
                                VariableSource.USER -> "1;32"    // Green
                            }
                            sb.append("\n  \u001B[${colorCode}m[${source.prefix}]\u001B[0m ")
                            sb.append("\u001B[1;37m${variable.name}\u001B[0m = ${variable.asString()}")
                        }
                    }
                }
                outputCallback(sb.toString())
            }
            return true
        }

        return false
    }

    /**
     * Загружает пользовательские переменные из Map (для загрузки конфига)
     */
    fun loadVariables(vars: Map<String, String>) {
        vars.forEach { (name, value) ->
            setVariable(name, value, VariableSource.USER)
        }
    }

    /**
     * Возвращает только пользовательские переменные для сохранения
     */
    fun getAllVariables(): Map<String, String> {
        return _variables.value
            .filterValues { it.source == VariableSource.USER }
            .mapValues { it.value.asString() }
    }

    /**
     * Возвращает все переменные (для отладки/скриптов)
     */
    fun getAllVariablesWithSource(): Map<String, Variable> {
        return _variables.value.toMap()
    }
}
