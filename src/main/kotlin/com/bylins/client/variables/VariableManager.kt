package com.bylins.client.variables

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Управляет пользовательскими переменными
 *
 * Переменные можно использовать в триггерах, алиасах и хоткеях
 * Синтаксис: @varname или ${varname}
 */
class VariableManager {
    private val _variables = MutableStateFlow<Map<String, String>>(emptyMap())
    val variables: StateFlow<Map<String, String>> = _variables

    /**
     * Устанавливает значение переменной
     */
    fun setVariable(name: String, value: String) {
        _variables.value = _variables.value + (name to value)
    }

    /**
     * Получает значение переменной
     */
    fun getVariable(name: String): String? {
        return _variables.value[name]
    }

    /**
     * Удаляет переменную
     */
    fun removeVariable(name: String) {
        _variables.value = _variables.value - name
    }

    /**
     * Очищает все переменные
     */
    fun clear() {
        _variables.value = emptyMap()
    }

    /**
     * Заменяет переменные в тексте
     * Поддерживает синтаксис @varname и ${varname}
     */
    fun substituteVariables(text: String): String {
        var result = text

        // Заменяем ${varname}
        val bracePattern = """\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}""".toRegex()
        result = bracePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            getVariable(varName) ?: matchResult.value
        }

        // Заменяем @varname (только если за ней не идёт буква/цифра)
        val atPattern = """@([a-zA-Z_][a-zA-Z0-9_]*)(?![a-zA-Z0-9_])""".toRegex()
        result = atPattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            getVariable(varName) ?: matchResult.value
        }

        return result
    }

    /**
     * Проверяет, является ли команда командой управления переменными
     * Обрабатывает: #var name value, #unvar name, #vars
     * Возвращает true, если команда обработана
     */
    fun processCommand(command: String, outputCallback: (String) -> Unit): Boolean {
        val trimmed = command.trim()

        // #var name value - установить переменную
        if (trimmed.startsWith("#var ")) {
            val parts = trimmed.substring(5).split(" ", limit = 2)
            if (parts.size >= 2) {
                val name = parts[0]
                val value = parts[1]
                setVariable(name, value)
                outputCallback("\u001B[1;32m[Переменная установлена: $name = $value]\u001B[0m")
                return true
            } else if (parts.size == 1) {
                // Показать значение переменной
                val name = parts[0]
                val value = getVariable(name)
                if (value != null) {
                    outputCallback("\u001B[1;36m[$name = $value]\u001B[0m")
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
                removeVariable(name)
                outputCallback("\u001B[1;33m[Переменная удалена: $name]\u001B[0m")
                return true
            }
        }

        // #vars - показать все переменные
        if (trimmed == "#vars") {
            val vars = _variables.value
            if (vars.isEmpty()) {
                outputCallback("\u001B[1;33m[Переменные не установлены]\u001B[0m")
            } else {
                outputCallback("\u001B[1;36m[Переменные (${vars.size}):]\u001B[0m")
                vars.entries.sortedBy { it.key }.forEach { (name, value) ->
                    outputCallback("\u001B[1;32m  $name\u001B[0m = \u001B[1;37m$value\u001B[0m")
                }
            }
            return true
        }

        return false
    }

    /**
     * Загружает переменные из Map
     */
    fun loadVariables(vars: Map<String, String>) {
        _variables.value = vars.toMap()
    }

    /**
     * Возвращает все переменные для сохранения
     */
    fun getAllVariables(): Map<String, String> {
        return _variables.value.toMap()
    }
}
