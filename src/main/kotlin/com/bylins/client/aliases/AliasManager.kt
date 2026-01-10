package com.bylins.client.aliases

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AliasManager(
    private val onCommand: (String) -> Unit,
    private val onAliasFired: ((Alias, String, Map<Int, String>) -> Unit)? = null
) {
    private val _aliases = MutableStateFlow<List<Alias>>(emptyList())
    val aliases: StateFlow<List<Alias>> = _aliases

    fun addAlias(alias: Alias) {
        _aliases.value = (_aliases.value + alias).sortedByDescending { it.priority }
    }

    fun removeAlias(id: String) {
        _aliases.value = _aliases.value.filter { it.id != id }
    }

    fun updateAlias(id: String, updater: (Alias) -> Alias) {
        _aliases.value = _aliases.value.map {
            if (it.id == id) updater(it) else it
        }.sortedByDescending { it.priority }
    }

    fun enableAlias(id: String) {
        updateAlias(id) { it.copy(enabled = true) }
    }

    fun disableAlias(id: String) {
        updateAlias(id) { it.copy(enabled = false) }
    }

    /**
     * Обрабатывает команду пользователя и применяет алиасы
     * Возвращает true если команда была обработана алиасом
     */
    fun processCommand(command: String): Boolean {
        for (alias in _aliases.value) {
            if (!alias.enabled) continue

            val matchResult = alias.pattern.matchEntire(command)
            if (matchResult != null) {
                // Уведомляем о срабатывании алиаса
                val groups = matchResult.groupValues.mapIndexed { index, value -> index to value }.toMap()
                onAliasFired?.invoke(alias, command, groups)

                executeAlias(alias, matchResult)
                return true
            }
        }
        return false
    }

    private fun executeAlias(alias: Alias, match: MatchResult) {
        for (command in alias.commands) {
            // Заменяем переменные $0, $1, $2 и т.д. на группы из regex
            var processedCommand = command
            for (i in 0 until match.groupValues.size) {
                processedCommand = processedCommand.replace("$$i", match.groupValues[i])
            }

            // Отправляем команду
            onCommand(processedCommand)
        }
    }

    /**
     * Загружает алиасы из конфига
     */
    fun loadAliases(aliases: List<Alias>) {
        _aliases.value = aliases.sortedByDescending { it.priority }
    }

    /**
     * Очищает все алиасы
     */
    fun clear() {
        _aliases.value = emptyList()
    }
}
