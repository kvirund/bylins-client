package com.bylins.client.tabs

import mu.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Менеджер вкладок
 */
private val logger = KotlinLogging.logger("TabManager")
class TabManager {
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId

    // Создаём AnsiParser один раз для всех строк
    private val ansiParser = com.bylins.client.ui.AnsiParser()

    /**
     * Главная вкладка (всегда существует)
     * Уменьшенный лимит для экономии памяти
     */
    private val mainTab = Tab(
        id = "main",
        name = "Главная",
        filters = emptyList(),
        captureMode = CaptureMode.COPY,
        maxLines = 1000  // Уменьшено с 2000
    )

    /**
     * Вкладка системных логов (всегда существует)
     */
    private val logsTab = Tab(
        id = "logs",
        name = "Логи",
        filters = emptyList(),
        captureMode = CaptureMode.COPY,
        maxLines = 500
    )

    init {
        // Добавляем системные вкладки (логи в конце - системная)
        _tabs.value = listOf(mainTab, logsTab)
        _activeTabId.value = "main"
    }

    /**
     * Добавляет новую вкладку (перед системной вкладкой "Логи")
     */
    fun addTab(tab: Tab) {
        if (_tabs.value.any { it.id == tab.id }) {
            logger.info { "Tab with id ${tab.id} already exists" }
            return
        }
        // Вставляем перед logsTab (который всегда последний)
        val current = _tabs.value.toMutableList()
        val logsIndex = current.indexOfFirst { it.id == "logs" }
        if (logsIndex >= 0) {
            current.add(logsIndex, tab)
        } else {
            current.add(tab)
        }
        _tabs.value = current
    }

    /**
     * Обновляет вкладку
     */
    fun updateTab(id: String, name: String, filters: List<TabFilter>, captureMode: CaptureMode) {
        if (id == "main") {
            logger.info { "Cannot update main tab" }
            return
        }
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id) {
                // Создаем новую вкладку с теми же параметрами, но новыми фильтрами
                val newTab = Tab(
                    id = tab.id,
                    name = name,
                    filters = filters,
                    captureMode = captureMode,
                    maxLines = tab.maxLines
                )
                // Копируем старое содержимое
                val oldContent = tab.content.value
                if (oldContent.isNotEmpty()) {
                    newTab.appendText(oldContent)
                }
                newTab
            } else {
                tab
            }
        }
    }

    /**
     * Удаляет вкладку
     */
    fun removeTab(id: String) {
        if (id == "main" || id == "logs") {
            logger.info { "Cannot remove system tab: $id" }
            return
        }
        _tabs.value = _tabs.value.filter { it.id != id }

        // Если удалили активную вкладку, переключаемся на главную
        if (_activeTabId.value == id) {
            _activeTabId.value = "main"
        }
    }

    /**
     * Получает вкладку по ID
     */
    fun getTab(id: String): Tab? {
        return _tabs.value.find { it.id == id }
    }

    /**
     * Устанавливает активную вкладку
     */
    fun setActiveTab(id: String) {
        if (_tabs.value.any { it.id == id }) {
            // Принудительно обновляем содержимое вкладки перед показом
            val tab = getTab(id)
            tab?.flush()
            // Сбрасываем индикатор непрочитанных
            tab?.markAsRead()
            _activeTabId.value = id
        }
    }

    /**
     * Обрабатывает входящий текст и распределяет его по вкладкам
     * Возвращает текст, который должен остаться в главной вкладке
     */
    fun processText(text: String): String {
        // Используем общий ansiParser вместо создания нового
        val lines = text.split("\n")
        val mainLines = mutableListOf<String>()

        for (line in lines) {
            var capturedByMove = false

            // Удаляем ANSI-коды для проверки фильтров
            val cleanLine = ansiParser.stripAnsi(line)

            // Проверяем каждую вкладку (кроме главной)
            val currentActiveTabId = _activeTabId.value
            for (tab in _tabs.value) {
                if (tab.id == "main") continue

                val transformedLine = tab.captureAndTransform(cleanLine, line)
                if (transformedLine != null) {
                    // Добавляем трансформированную строку в эту вкладку
                    // Помечаем как непрочитанную если вкладка не активна
                    val isActive = tab.id == currentActiveTabId
                    tab.appendText(transformedLine, markUnread = !isActive)

                    // Если режим MOVE, помечаем что не нужно добавлять в main
                    if (tab.captureMode == CaptureMode.MOVE) {
                        capturedByMove = true
                    }
                }
            }

            // Добавляем в главную вкладку, если не было захвачено с MOVE
            if (!capturedByMove) {
                mainLines.add(line)
            }
        }

        val mainText = mainLines.joinToString("\n")

        // Добавляем в главную вкладку
        if (mainText.isNotEmpty()) {
            mainTab.appendText(mainText)
        }

        // Принудительно обновляем UI для всех вкладок
        mainTab.flush()
        for (tab in _tabs.value) {
            if (tab.id != "main") {
                tab.flush()
            }
        }

        return mainText
    }

    /**
     * Добавляет текст напрямую в главную вкладку (без фильтрации)
     * Используется для системных сообщений от скриптов/плагинов
     * Примечание: для отображения используется receivedData, это только для сохранения в лог
     */
    fun addToMainTab(text: String) {
        if (text.isEmpty()) return

        mainTab.appendText(text)
        mainTab.flush()
    }

    /**
     * Добавляет текст в вкладку системных логов
     */
    fun addToLogsTab(text: String) {
        if (text.isEmpty()) return

        val isActive = _activeTabId.value == "logs"
        logsTab.appendText(text, markUnread = !isActive)
        logsTab.flush()
    }

    /**
     * Очищает все вкладки
     */
    fun clearAll() {
        _tabs.value.forEach { it.clear() }
    }

    /**
     * Очищает конкретную вкладку
     */
    fun clearTab(id: String) {
        getTab(id)?.clear()
    }

    /**
     * Загружает вкладки из списка
     */
    fun loadTabs(tabs: List<Tab>) {
        // Ищем сохранённые системные вкладки
        val savedMainTab = tabs.find { it.id == "main" }
        val savedLogsTab = tabs.find { it.id == "logs" }
        val otherTabs = tabs.filter { it.id != "main" && it.id != "logs" }

        // Восстанавливаем содержимое главной вкладки
        if (savedMainTab != null) {
            val savedContent = savedMainTab.content.value
            if (savedContent.isNotEmpty()) {
                mainTab.appendText(savedContent)
                mainTab.flush()
            }
        }

        // Восстанавливаем содержимое вкладки логов
        if (savedLogsTab != null) {
            val savedContent = savedLogsTab.content.value
            if (savedContent.isNotEmpty()) {
                logsTab.appendText(savedContent)
                logsTab.flush()
            }
        }

        // Добавляем welcome message после восстановленного лога
        mainTab.appendText("\nДобро пожаловать в Bylins MUD Client!\nПодключитесь к серверу для начала игры.\n")
        mainTab.flush()

        // Сохраняем: mainTab, пользовательские вкладки, logsTab (в конце)
        _tabs.value = listOf(mainTab) + otherTabs + listOf(logsTab)
    }

    /**
     * Возвращает все вкладки (включая главную) для сохранения
     */
    fun getTabsForSave(): List<Tab> {
        return _tabs.value
    }
}
