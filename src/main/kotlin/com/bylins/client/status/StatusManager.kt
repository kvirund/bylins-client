package com.bylins.client.status

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.bylins.client.variables.VariableManager
import com.bylins.client.variables.VariableSource

/**
 * Менеджер статус-панели с API для скриптов
 *
 * Позволяет скриптам добавлять, обновлять и удалять элементы статуса.
 * Автоматически отражает состояние в readonly переменные с префиксом _status_
 */
class StatusManager(
    private val variableManager: VariableManager
) {
    private val _elements = MutableStateFlow<Map<String, StatusElement>>(emptyMap())
    val elements: StateFlow<Map<String, StatusElement>> = _elements

    /**
     * Получает отсортированный список элементов
     */
    fun getOrderedElements(): List<StatusElement> {
        return _elements.value.values.sortedBy { element ->
            when (element) {
                is StatusElement.Bar -> element.order
                is StatusElement.Text -> element.order
                is StatusElement.Flags -> element.order
                is StatusElement.MiniMap -> element.order
            }
        }
    }

    /**
     * Добавляет или обновляет прогресс-бар
     */
    fun addBar(
        id: String,
        label: String,
        value: Int,
        max: Int,
        color: String = "green",
        showText: Boolean = true,
        order: Int = _elements.value.size
    ) {
        val bar = StatusElement.Bar(id, label, value, max, color, showText, order)
        _elements.value = _elements.value + (id to bar)
        updateStatusVariable(id, bar)
    }

    /**
     * Добавляет или обновляет текстовый элемент
     */
    fun addText(
        id: String,
        label: String,
        value: String,
        order: Int = _elements.value.size
    ) {
        val text = StatusElement.Text(id, label, value, order)
        _elements.value = _elements.value + (id to text)
        updateStatusVariable(id, text)
    }

    /**
     * Добавляет или обновляет список флагов
     */
    fun addFlags(
        id: String,
        label: String,
        flags: List<FlagItem>,
        order: Int = _elements.value.size
    ) {
        val flagsElement = StatusElement.Flags(id, label, flags, order)
        _elements.value = _elements.value + (id to flagsElement)
        updateStatusVariable(id, flagsElement)
    }

    /**
     * Добавляет или обновляет миникарту
     */
    fun addMiniMap(
        id: String,
        currentRoomId: String? = null,
        visible: Boolean = true,
        order: Int = _elements.value.size
    ) {
        val miniMap = StatusElement.MiniMap(id, currentRoomId, visible, order)
        _elements.value = _elements.value + (id to miniMap)
        updateStatusVariable(id, miniMap)
    }

    /**
     * Обновляет существующий элемент
     * @param updates Map с полями для обновления
     */
    fun update(id: String, updates: Map<String, Any>) {
        val existing = _elements.value[id] ?: return

        val updated = when (existing) {
            is StatusElement.Bar -> existing.copy(
                label = updates["label"] as? String ?: existing.label,
                value = (updates["value"] as? Number)?.toInt() ?: existing.value,
                max = (updates["max"] as? Number)?.toInt() ?: existing.max,
                color = updates["color"] as? String ?: existing.color,
                showText = updates["showText"] as? Boolean ?: existing.showText,
                order = (updates["order"] as? Number)?.toInt() ?: existing.order
            )
            is StatusElement.Text -> existing.copy(
                label = updates["label"] as? String ?: existing.label,
                value = updates["value"] as? String ?: existing.value,
                order = (updates["order"] as? Number)?.toInt() ?: existing.order
            )
            is StatusElement.Flags -> {
                @Suppress("UNCHECKED_CAST")
                val newFlags = updates["flags"] as? List<FlagItem>
                existing.copy(
                    label = updates["label"] as? String ?: existing.label,
                    flags = newFlags ?: existing.flags,
                    order = (updates["order"] as? Number)?.toInt() ?: existing.order
                )
            }
            is StatusElement.MiniMap -> existing.copy(
                currentRoomId = updates["currentRoomId"] as? String ?: existing.currentRoomId,
                visible = updates["visible"] as? Boolean ?: existing.visible,
                order = (updates["order"] as? Number)?.toInt() ?: existing.order
            )
        }

        _elements.value = _elements.value + (id to updated)
        updateStatusVariable(id, updated)
    }

    /**
     * Удаляет элемент по ID
     */
    fun remove(id: String) {
        _elements.value = _elements.value - id
        variableManager.removeVariableBySource("_status_$id", VariableSource.STATUS)
    }

    /**
     * Очищает все элементы статуса
     */
    fun clear() {
        val ids = _elements.value.keys.toList()
        _elements.value = emptyMap()
        ids.forEach { id ->
            variableManager.removeVariableBySource("_status_$id", VariableSource.STATUS)
        }
    }

    /**
     * Получает элемент по ID
     */
    fun get(id: String): StatusElement? = _elements.value[id]

    /**
     * Проверяет, существует ли элемент
     */
    fun exists(id: String): Boolean = id in _elements.value

    /**
     * Обновляет readonly переменную для элемента статуса
     */
    private fun updateStatusVariable(id: String, element: StatusElement) {
        val value = when (element) {
            is StatusElement.Bar -> mapOf(
                "type" to "bar",
                "label" to element.label,
                "value" to element.value,
                "max" to element.max,
                "color" to element.color,
                "showText" to element.showText
            )
            is StatusElement.Text -> mapOf(
                "type" to "text",
                "label" to element.label,
                "value" to element.value
            )
            is StatusElement.Flags -> mapOf(
                "type" to "flags",
                "label" to element.label,
                "flags" to element.flags.map { flag ->
                    mapOf(
                        "name" to flag.name,
                        "active" to flag.active,
                        "color" to flag.color,
                        "timer" to (flag.timer ?: "")
                    )
                }
            )
            is StatusElement.MiniMap -> mapOf(
                "type" to "minimap",
                "currentRoomId" to (element.currentRoomId ?: ""),
                "visible" to element.visible
            )
        }

        variableManager.setStatusVariable("_status_$id", value)
    }
}
