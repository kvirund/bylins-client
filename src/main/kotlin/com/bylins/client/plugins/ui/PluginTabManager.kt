package com.bylins.client.plugins.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for plugin tabs.
 * Maintains global state of all plugin tabs for UI rendering.
 */
class PluginTabManager {
    private val tabs = ConcurrentHashMap<String, PluginTab>()
    private val _tabsState = MutableStateFlow<List<PluginTab>>(emptyList())

    /** Observable list of all plugin tabs */
    val tabsState: StateFlow<List<PluginTab>> = _tabsState

    /**
     * Registers a new plugin tab.
     */
    fun registerTab(tab: PluginTab) {
        tabs[tab.id] = tab
        updateState()
    }

    /**
     * Unregisters a plugin tab by ID.
     */
    fun unregisterTab(tabId: String) {
        tabs.remove(tabId)
        updateState()
    }

    /**
     * Gets a tab by ID.
     */
    fun getTab(tabId: String): PluginTab? = tabs[tabId]

    /**
     * Gets all tabs.
     */
    fun getAllTabs(): List<PluginTab> = tabs.values.toList()

    private fun updateState() {
        _tabsState.value = tabs.values.toList()
    }
}
