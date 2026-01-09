package com.bylins.client.triggers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TriggerManager(
    private val onCommand: (String) -> Unit
) {
    private val _triggers = MutableStateFlow<List<Trigger>>(emptyList())
    val triggers: StateFlow<List<Trigger>> = _triggers

    private val firedOnceTriggers = mutableSetOf<String>()

    fun addTrigger(trigger: Trigger) {
        _triggers.value = (_triggers.value + trigger).sortedByDescending { it.priority }
    }

    fun removeTrigger(id: String) {
        _triggers.value = _triggers.value.filter { it.id != id }
        firedOnceTriggers.remove(id)
    }

    fun updateTrigger(id: String, updater: (Trigger) -> Trigger) {
        _triggers.value = _triggers.value.map {
            if (it.id == id) updater(it) else it
        }.sortedByDescending { it.priority }
    }

    fun enableTrigger(id: String) {
        updateTrigger(id) { it.copy(enabled = true) }
    }

    fun disableTrigger(id: String) {
        updateTrigger(id) { it.copy(enabled = false) }
    }

    /**
     * Обрабатывает строку текста и возвращает список совпавших триггеров
     */
    fun processLine(line: String): List<TriggerMatch> {
        val matches = mutableListOf<TriggerMatch>()

        for (trigger in _triggers.value) {
            if (!trigger.enabled) continue
            if (trigger.once && firedOnceTriggers.contains(trigger.id)) continue

            val matchResult = trigger.pattern.find(line)
            if (matchResult != null) {
                matches.add(TriggerMatch(trigger, matchResult, line))

                // Выполняем команды триггера
                executeTrigger(trigger, matchResult)

                // Помечаем once триггер как выполненный
                if (trigger.once) {
                    firedOnceTriggers.add(trigger.id)
                }
            }
        }

        return matches
    }

    private fun executeTrigger(trigger: Trigger, match: MatchResult) {
        for (command in trigger.commands) {
            // Заменяем переменные $1, $2 и т.д. на группы из regex
            var processedCommand = command
            for (i in 0..match.groupValues.size - 1) {
                processedCommand = processedCommand.replace("$$i", match.groupValues[i])
            }

            // Отправляем команду
            onCommand(processedCommand)
        }
    }

    /**
     * Проверяет должна ли строка быть скрыта (gag)
     */
    fun shouldGag(line: String): Boolean {
        return _triggers.value
            .filter { it.enabled && it.gag }
            .any { it.pattern.find(line) != null }
    }

    /**
     * Очищает все once триггеры (например при переподключении)
     */
    fun resetOnceTriggers() {
        firedOnceTriggers.clear()
    }

    /**
     * Загружает триггеры из конфига
     */
    fun loadTriggers(triggers: List<Trigger>) {
        _triggers.value = triggers.sortedByDescending { it.priority }
    }

    /**
     * Очищает все триггеры
     */
    fun clear() {
        _triggers.value = emptyList()
        firedOnceTriggers.clear()
    }
}
