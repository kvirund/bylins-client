# Система вкладок и перенаправления

## Обзор

Система множественных вкладок позволяет организовать вывод от MUD сервера в различные окна по категориям.

## Use Cases

### 1. Разделение чата и игры
```
Main: основной геймплей
Chat: tells, шепоты, оос
Channels: болталка, новости, кланы
```

### 2. Бой отдельно
```
Main: исследование мира
Combat: весь боевой лог
```

### 3. Групповая игра
```
Main: основное
Party: групповые сообщения, hp группы
Combat: бой
```

## Архитектура

### Tab Model
```kotlin
data class Tab(
    val id: String,
    val name: String,
    val content: StateFlow<AnnotatedString>,
    val unreadCount: Int = 0,
    val color: Color? = null
)

class TabManager {
    private val _tabs = MutableStateFlow<List<Tab>>(listOf(mainTab))
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _activeTab = MutableStateFlow<String>("main")
    val activeTab: StateFlow<String> = _activeTab

    fun createTab(name: String): Tab
    fun closeTab(id: String)
    fun switchTab(id: String)
    fun renameTab(id: String, newName: String)
}
```

### Redirect Rules
```kotlin
data class RedirectRule(
    val id: String,
    val pattern: Regex,
    val targetTab: String,
    val mode: RedirectMode,
    val enabled: Boolean = true
)

enum class RedirectMode {
    COPY,           // Показать в обеих вкладках (Main + Target)
    MOVE,           // Показать только в Target (gag в Main)
    COPY_HIGHLIGHT  // Копировать с подсветкой
}

class RedirectManager {
    private val rules = mutableListOf<RedirectRule>()

    fun addRule(rule: RedirectRule)
    fun removeRule(id: String)
    fun processLine(line: String): Map<String, String> {
        // Возвращает map: tabId -> text
        // Одна строка может попасть в несколько вкладок
    }
}
```

### Text Processing Pipeline
```kotlin
class TextProcessor(
    private val tabManager: TabManager,
    private val redirectManager: RedirectManager,
    private val triggerManager: TriggerManager
) {
    fun processServerOutput(text: String) {
        val lines = text.split("\n")

        for (line in lines) {
            // 1. Выполнить триггеры
            triggerManager.process(line)

            // 2. Проверить redirect rules
            val redirects = redirectManager.processLine(line)

            // 3. Добавить в соответствующие вкладки
            for ((tabId, content) in redirects) {
                tabManager.appendToTab(tabId, content)
            }
        }
    }
}
```

## UI Design

### Tab Bar
```
┌─────────────────────────────────────────────────┐
│ [Main] [Chat(3)] [Combat] [+] [⚙️]              │
└─────────────────────────────────────────────────┘
```

- `[Main]` - активная вкладка
- `[Chat(3)]` - 3 непрочитанных сообщения
- `[+]` - создать новую вкладку
- `[⚙️]` - настройки redirect rules

### Redirect Rules Editor
```
┌─────────────────────────────────────────────────┐
│ Redirect Rules                                  │
├─────────────────────────────────────────────────┤
│ ☑ ^.+ говорит вам:       → [Chat]    [Copy]    │
│ ☑ ^.+ шепчет вам:        → [Chat]    [Copy]    │
│ ☑ ^\[Болталка\]          → [Channels] [Move]   │
│ ☑ ^Вы атакуете           → [Combat]  [Copy]    │
│ ☐ ^.+ атакует вас        → [Combat]  [Copy]    │
├─────────────────────────────────────────────────┤
│ [Add Rule] [Edit] [Delete] [Import] [Export]   │
└─────────────────────────────────────────────────┘
```

## Конфигурация

### tabs.json
```json
{
  "tabs": [
    {
      "id": "main",
      "name": "Main",
      "permanent": true
    },
    {
      "id": "chat",
      "name": "Chat",
      "color": "#00AA00"
    },
    {
      "id": "combat",
      "name": "Combat",
      "color": "#AA0000"
    }
  ],
  "redirectRules": [
    {
      "id": "tells",
      "pattern": "^.+ говорит вам:",
      "targetTab": "chat",
      "mode": "COPY",
      "enabled": true
    },
    {
      "id": "whispers",
      "pattern": "^.+ шепчет вам:",
      "targetTab": "chat",
      "mode": "COPY",
      "enabled": true
    },
    {
      "id": "channels",
      "pattern": "^\\[Болталка\\]",
      "targetTab": "chat",
      "mode": "MOVE",
      "enabled": true
    }
  ]
}
```

## Примеры использования

### Пример 1: Чат
```kotlin
// Создаем вкладку для чата
tabManager.createTab("Chat")

// Добавляем правила
redirectManager.addRule(
    RedirectRule(
        pattern = "^.+ говорит вам:".toRegex(),
        targetTab = "chat",
        mode = RedirectMode.COPY
    )
)
```

Результат:
```
Main:  обычный геймплей + копии tells
Chat:  только tells и шепоты
```

### Пример 2: Бой (gag в Main)
```kotlin
tabManager.createTab("Combat")

redirectManager.addRule(
    RedirectRule(
        pattern = "^(Вы атакуете|.+ атакует вас)".toRegex(),
        targetTab = "combat",
        mode = RedirectMode.MOVE  // Убрать из Main
    )
)
```

Результат:
```
Main:    чистый от боевого спама
Combat:  весь боевой лог
```

## Горячие клавиши

- `Ctrl+T` - новая вкладка
- `Ctrl+W` - закрыть вкладку
- `Ctrl+Tab` - следующая вкладка
- `Ctrl+Shift+Tab` - предыдущая вкладка
- `Ctrl+1..9` - переключиться на вкладку N

## Split Windows (будущее)

```
┌─────────────────┬──────────────┐
│                 │              │
│   Main          │   Chat       │
│                 │              │
│                 ├──────────────┤
│                 │              │
│                 │   Combat     │
└─────────────────┴──────────────┘
```

## Detached Windows (будущее)

Возможность "отцепить" вкладку в отдельное окно:
- Независимое позиционирование
- Always on top режим
- Настраиваемая прозрачность
- Идеально для мониторинга чата на втором мониторе
