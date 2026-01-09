# Примеры триггеров и алиасов

## Триггеры

### Пример 1: Auto-heal при низком HP
```kotlin
val autoHealTrigger = Trigger(
    id = "auto-heal",
    name = "Auto Heal",
    pattern = "HP: (\\d+)/(\\d+)".toRegex(),
    commands = listOf("cast 'cure serious'"),
    enabled = true,
    priority = 10
)

clientState.addTrigger(autoHealTrigger)
```

### Пример 2: Автолут с трупов
```kotlin
val autoLootTrigger = Trigger(
    id = "auto-loot",
    name = "Auto Loot",
    pattern = "^(.+) мертв".toRegex(),
    commands = listOf(
        "взять все $1",
        "взять все.монета труп"
    ),
    enabled = true
)

clientState.addTrigger(autoLootTrigger)
```

### Пример 3: Уведомление при tells
```kotlin
val tellNotifyTrigger = Trigger(
    id = "tell-notify",
    name = "Tell Notification",
    pattern = "^(.+) говорит вам:".toRegex(),
    commands = emptyList(), // Только уведомление, без команд
    enabled = true,
    colorize = TriggerColorize(
        foreground = "#00FF00",
        bold = true
    )
)

clientState.addTrigger(tellNotifyTrigger)
```

### Пример 4: Gag спама
```kotlin
val gagSpamTrigger = Trigger(
    id = "gag-spam",
    name = "Gag Spam",
    pattern = "^\\[Болталка\\]".toRegex(),
    commands = emptyList(),
    enabled = true,
    gag = true  // Скрыть эту строку
)

clientState.addTrigger(gagSpamTrigger)
```

### Пример 5: Once триггер (одноразовый)
```kotlin
val welcomeTrigger = Trigger(
    id = "welcome",
    name = "Welcome Message",
    pattern = "Добро пожаловать в мир Былин".toRegex(),
    commands = listOf("кто"),
    enabled = true,
    once = true  // Сработает только один раз
)

clientState.addTrigger(welcomeTrigger)
```

## Алиасы

### Пример 1: Простой алиас (сокращение)
```kotlin
val recallAlias = Alias(
    id = "recall-alias",
    name = "Recall",
    pattern = "^r$".toRegex(),
    commands = listOf("cast 'word of recall'"),
    enabled = true
)

clientState.addAlias(recallAlias)
```

Использование:
```
> r
// Отправит: cast 'word of recall'
```

### Пример 2: Алиас с параметрами
```kotlin
val tellAlias = Alias(
    id = "tell-alias",
    name = "Tell",
    pattern = "^t (\\w+) (.+)$".toRegex(),
    commands = listOf("tell $1 $2"),
    enabled = true
)

clientState.addAlias(tellAlias)
```

Использование:
```
> t Вася привет
// Отправит: tell Вася привет
```

### Пример 3: Speedwalk
```kotlin
val speedwalkAlias = Alias(
    id = "speedwalk",
    name = "Speedwalk",
    pattern = "^#(\\d+)([nsewud])$".toRegex(),
    commands = listOf("$2"), // Будет выполнено N раз
    enabled = true
)

clientState.addAlias(speedwalkAlias)
```

Использование:
```
> #5n
// Отправит: n, n, n, n, n (5 раз на север)
```

Примечание: для этого нужна модификация AliasManager для поддержки повторений.

### Пример 4: Мульти-команда
```kotlin
val buffAlias = Alias(
    id = "buff",
    name = "Buff",
    pattern = "^buff$".toRegex(),
    commands = listOf(
        "cast 'armor'",
        "cast 'bless'",
        "cast 'shield'"
    ),
    enabled = true
)

clientState.addAlias(buffAlias)
```

Использование:
```
> buff
// Отправит последовательно:
// cast 'armor'
// cast 'bless'
// cast 'shield'
```

### Пример 5: Алиас с regex группами
```kotlin
val castAlias = Alias(
    id = "cast-alias",
    name = "Cast Shortcut",
    pattern = "^c '(.+)'( (.+))?$".toRegex(),
    commands = listOf("cast '$1'$2"),
    enabled = true
)

clientState.addAlias(castAlias)
```

Использование:
```
> c 'fireball' василий
// Отправит: cast 'fireball' василий
```

## Программное использование

### Инициализация при запуске
```kotlin
class ClientState {
    init {
        // Загружаем стандартные триггеры и алиасы
        loadDefaultTriggers()
        loadDefaultAliases()
    }

    private fun loadDefaultTriggers() {
        addTrigger(autoHealTrigger)
        addTrigger(autoLootTrigger)
        // и т.д.
    }

    private fun loadDefaultAliases() {
        addAlias(recallAlias)
        addAlias(tellAlias)
        // и т.д.
    }
}
```

### Динамическое добавление
```kotlin
// Добавить триггер
clientState.addTrigger(
    Trigger(
        id = "custom-trigger",
        name = "My Trigger",
        pattern = "pattern".toRegex(),
        commands = listOf("command")
    )
)

// Удалить триггер
clientState.removeTrigger("custom-trigger")

// Включить/выключить
clientState.enableTrigger("auto-heal")
clientState.disableTrigger("auto-heal")
```

## Сохранение и загрузка (TODO)

### JSON формат
```json
{
  "triggers": [
    {
      "id": "auto-heal",
      "name": "Auto Heal",
      "pattern": "HP: (\\d+)/(\\d+)",
      "commands": ["cast 'cure serious'"],
      "enabled": true,
      "priority": 10
    }
  ],
  "aliases": [
    {
      "id": "recall-alias",
      "name": "Recall",
      "pattern": "^r$",
      "commands": ["cast 'word of recall'"],
      "enabled": true
    }
  ]
}
```

### Загрузка из файла (планируется)
```kotlin
val config = File("config/triggers.json").readText()
val triggers = Json.decodeFromString<List<Trigger>>(config)
triggerManager.loadTriggers(triggers)
```
