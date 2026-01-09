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
     */
    fun processKeyPress(
        key: Key,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean
    ): Boolean {
        for (hotkey in _hotkeys.value) {
            if (!hotkey.enabled) continue

            if (hotkey.matches(key, isCtrlPressed, isAltPressed, isShiftPressed)) {
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
}
