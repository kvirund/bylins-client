package com.bylins.client.hotkeys

import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HotkeyManager(
    private val onCommand: (String) -> Unit
) {
    private val _hotkeys = MutableStateFlow<List<Hotkey>>(emptyList())
    val hotkeys: StateFlow<List<Hotkey>> = _hotkeys

    fun addHotkey(hotkey: Hotkey) {
        _hotkeys.value = _hotkeys.value + hotkey
    }

    fun removeHotkey(id: String) {
        _hotkeys.value = _hotkeys.value.filter { it.id != id }
    }

    fun updateHotkey(id: String, updater: (Hotkey) -> Hotkey) {
        _hotkeys.value = _hotkeys.value.map {
            if (it.id == id) updater(it) else it
        }
    }

    fun enableHotkey(id: String) {
        updateHotkey(id) { it.copy(enabled = true) }
    }

    fun disableHotkey(id: String) {
        updateHotkey(id) { it.copy(enabled = false) }
    }

    /**
     * Обрабатывает нажатие клавиши и возвращает true, если хоткей сработал
     * @param ignoreNumLock если true, NumPad клавиши работают независимо от состояния NumLock
     */
    fun processKeyPress(
        key: Key,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean,
        ignoreNumLock: Boolean = false
    ): Boolean {
        for (hotkey in _hotkeys.value) {
            if (!hotkey.enabled) continue

            if (hotkey.matches(key, isCtrlPressed, isAltPressed, isShiftPressed, ignoreNumLock)) {
                executeHotkey(hotkey)
                return true
            }
        }
        return false
    }

    private fun executeHotkey(hotkey: Hotkey) {
        for (command in hotkey.commands) {
            onCommand(command)
        }
    }

    /**
     * Загружает хоткеи из конфига
     */
    fun loadHotkeys(hotkeys: List<Hotkey>) {
        _hotkeys.value = hotkeys
    }

    /**
     * Очищает все хоткеи
     */
    fun clear() {
        _hotkeys.value = emptyList()
    }

    /**
     * Экспортирует выбранные хоткеи в JSON строку
     */
    fun exportHotkeys(hotkeyIds: List<String>): String {
        val selectedHotkeys = _hotkeys.value.filter { it.id in hotkeyIds }
        val dtos = selectedHotkeys.map { com.bylins.client.config.HotkeyDto.fromHotkey(it) }
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.bylins.client.config.HotkeyDto.serializer()),
            dtos
        )
    }

    /**
     * Импортирует хоткеи из JSON строки
     * @param json JSON строка с хоткеями
     * @param merge если true, добавляет к существующим, иначе заменяет конфликтующие
     * @return количество импортированных хоткеев
     */
    fun importHotkeys(json: String, merge: Boolean = true): Int {
        try {
            val dtos = kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.bylins.client.config.HotkeyDto.serializer()),
                json
            )
            val hotkeys = dtos.mapNotNull { it.toHotkey() }

            if (merge) {
                // Добавляем новые хоткеи, заменяя существующие с теми же ID
                val importedIds = hotkeys.map { it.id }.toSet()
                val newHotkeys = _hotkeys.value.filter { it.id !in importedIds } + hotkeys
                _hotkeys.value = newHotkeys
            } else {
                // Полная замена
                _hotkeys.value = hotkeys
            }

            return hotkeys.size
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to import hotkeys: ${e.message}", e)
        }
    }
}
