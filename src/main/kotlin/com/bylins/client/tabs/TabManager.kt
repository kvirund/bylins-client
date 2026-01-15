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

    init {
        // Добавляем главную вкладку
        _tabs.value = listOf(mainTab)
        _activeTabId.value = "main"
    }

    /**
     * Добавляет новую вкладку
     */
    fun addTab(tab: Tab) {
        if (_tabs.value.any { it.id == tab.id }) {
            logger.info { "Tab with id ${tab.id} already exists" }
            return
        }
        _tabs.value = _tabs.value + tab
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
        if (id == "main") {
            logger.info { "Cannot remove main tab" }
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
            getTab(id)?.flush()
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
            for (tab in _tabs.value) {
                if (tab.id == "main") continue

                val transformedLine = tab.captureAndTransform(cleanLine, line)
                if (transformedLine != null) {
                    // Добавляем трансформированную строку в эту вкладку
                    tab.appendText(transformedLine)

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
        // Ищем сохранённую главную вкладку
        val savedMainTab = tabs.find { it.id == "main" }
        val otherTabs = tabs.filter { it.id != "main" }

        // Восстанавливаем содержимое главной вкладки
        if (savedMainTab != null) {
            val savedContent = savedMainTab.content.value
            if (savedContent.isNotEmpty()) {
                mainTab.appendText(savedContent)
                mainTab.flush()
            }
        }

        // Добавляем welcome message после восстановленного лога
        mainTab.appendText("\nДобро пожаловать в Bylins MUD Client!\nПодключитесь к серверу для начала игры.\n")
        mainTab.flush()

        // Сохраняем главную вкладку и добавляем загруженные
        _tabs.value = listOf(mainTab) + otherTabs
    }

    /**
     * Возвращает все вкладки (включая главную) для сохранения
     */
    fun getTabsForSave(): List<Tab> {
        return _tabs.value
    }
}
