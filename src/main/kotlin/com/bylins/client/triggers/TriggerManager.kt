package com.bylins.client.triggers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TriggerManager(
    private val onCommand: (String) -> Unit,
    private val onTriggerFired: ((Trigger, String, Map<Int, String>) -> Unit)? = null
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

                // Уведомляем о срабатывании триггера
                val groups = matchResult.groupValues.mapIndexed { index, value -> index to value }.toMap()
                onTriggerFired?.invoke(trigger, line, groups)

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

    /**
     * Обрабатывает строку текста против указанного списка триггеров
     * Используется для обработки триггеров из профилей
     */
    fun processLineWithTriggers(line: String, externalTriggers: List<Trigger>): List<TriggerMatch> {
        val matches = mutableListOf<TriggerMatch>()

        for (trigger in externalTriggers) {
            if (!trigger.enabled) continue
            if (trigger.once && firedOnceTriggers.contains(trigger.id)) continue

            val matchResult = trigger.pattern.find(line)
            if (matchResult != null) {
                matches.add(TriggerMatch(trigger, matchResult, line))

                // Уведомляем о срабатывании триггера
                val groups = matchResult.groupValues.mapIndexed { index, value -> index to value }.toMap()
                onTriggerFired?.invoke(trigger, line, groups)

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
                processedCommand = processedCommand.replace("\$$i", match.groupValues[i])
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

    /**
     * Экспортирует выбранные триггеры в JSON строку
     */
    fun exportTriggers(triggerIds: List<String>): String {
        val selectedTriggers = _triggers.value.filter { it.id in triggerIds }
        val dtos = selectedTriggers.map { com.bylins.client.config.TriggerDto.fromTrigger(it) }
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.bylins.client.config.TriggerDto.serializer()),
            dtos
        )
    }

    /**
     * Импортирует триггеры из JSON строки
     * @param json JSON строка с триггерами
     * @param merge если true, добавляет к существующим, иначе заменяет конфликтующие
     * @return количество импортированных триггеров
     */
    fun importTriggers(json: String, merge: Boolean = true): Int {
        try {
            val dtos = kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.bylins.client.config.TriggerDto.serializer()),
                json
            )
            val triggers = dtos.map { it.toTrigger() }

            if (merge) {
                // Добавляем новые триггеры, заменяя существующие с теми же ID
                val importedIds = triggers.map { it.id }.toSet()
                val newTriggers = _triggers.value.filter { it.id !in importedIds } + triggers
                _triggers.value = newTriggers.sortedByDescending { it.priority }
            } else {
                // Полная замена
                _triggers.value = triggers.sortedByDescending { it.priority }
            }

            return triggers.size
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to import triggers: \${e.message}", e)
        }
    }
}
