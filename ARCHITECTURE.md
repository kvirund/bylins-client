# Архитектура проекта

## Обзор

Bylins MUD Client построен на современном стеке:
- **Kotlin** - основной язык
- **Compose for Desktop** - UI фреймворк
- **Coroutines** - асинхронность
- **Gradle** - сборка проекта

## Структура модулей

### 1. UI Layer (`ui/`)

#### MainWindow.kt
Главное окно приложения с layout:
- Верхняя панель подключения
- Основная область (текст + статус)
- Панель ввода команд

#### Components

**ConnectionPanel** - управление подключением
- Поля host/port
- Кнопка connect/disconnect
- Состояние подключения

**OutputPanel** - вывод текста от сервера
- Monospace шрифт
- Прокрутка
- Выделение текста
- TODO: ANSI цвета

**InputPanel** - ввод команд
- История команд (↑/↓)
- Enter для отправки
- TODO: автодополнение

**StatusPanel** - статус персонажа
- HP/Mana/Move бары
- Уровень, опыт, золото
- TODO: динамические данные из MSDP

### 2. Network Layer (`network/`)

#### TelnetClient.kt
Основной класс для работы с Telnet:
- Подключение/отключение
- Отправка команд
- Прием данных
- Telnet negotiation (DO/DONT/WILL/WONT)
- Поддержка MSDP, GMCP

**Flow архитектура:**
```
connect() -> startReading() -> parse() -> emit receivedData
                            -> parse() -> handleTelnetCommand()
```

**Поддерживаемые Telnet опции:**
- TERMINAL_TYPE (24)
- NAWS (31) - размер окна
- MSDP (69) - данные сервера
- GMCP (201) - расширенный протокол

#### TelnetParser.kt
Парсер Telnet протокола:
- State machine для обработки IAC последовательностей
- Разделение текста и команд
- Обработка subnegotiation

**States:**
- NORMAL - обычный текст
- IAC - обнаружен IAC (255)
- COMMAND - обработка DO/DONT/WILL/WONT
- SUBNEGOTIATION - обработка SB...SE
- SUBNEG_IAC - IAC внутри subnegotiation

### 3. TODO: Triggers System (`triggers/`)

Планируется:
```kotlin
data class Trigger(
    val pattern: Regex,
    val action: (MatchResult) -> Unit,
    val enabled: Boolean = true
)

class TriggerManager {
    fun addTrigger(trigger: Trigger)
    fun removeTrigger(id: String)
    fun process(text: String)
}
```

### 4. TODO: Mapper System (`mapper/`)

Автомаппер с Canvas/SVG:
```kotlin
data class Room(
    val id: String,
    val name: String,
    val exits: Map<Direction, String>,
    val x: Int, y: Int, z: Int
)

class Mapper {
    fun addRoom(room: Room)
    fun navigate(direction: Direction)
    fun render(): Bitmap
}
```

### 5. TODO: Stats System (`stats/`)

Система статистики:
- Сбор данных (урон, лечение, опыт)
- Хранение в БД или файлах
- Графики (Compose Charts)

### 6. TODO: Plugins System (`plugins/`)

Система плагинов:
```kotlin
interface Plugin {
    val name: String
    val version: String
    fun onLoad()
    fun onUnload()
    fun onCommand(cmd: String): Boolean
}

class PluginManager {
    fun loadPlugin(path: String)
    fun unloadPlugin(name: String)
}
```

## Data Flow

```
User Input -> InputPanel -> TelnetClient.send() -> Server
Server -> TelnetClient.receive() -> TelnetParser -> OutputPanel
                                                 -> TriggerManager
                                                 -> MSDP Handler -> StatusPanel
```

## State Management

Используется Kotlin StateFlow для реактивности:
```kotlin
// TelnetClient
val isConnected: StateFlow<Boolean>
val receivedData: StateFlow<String>

// UI Components наблюдают за состоянием
val isConnected by telnetClient.isConnected.collectAsState()
```

## Конфигурация

TODO: Планируется JSON конфиги:
- `config/settings.json` - основные настройки
- `config/triggers.json` - триггеры
- `config/aliases.json` - алиасы
- `config/colors.json` - цветовая схема

## Расширяемость

### Добавление новой Telnet опции
1. Добавить константу в TelnetClient
2. Добавить в sendTelnetNegotiation()
3. Добавить обработчик в handleTelnetCommand()

### Добавление нового UI компонента
1. Создать Composable в `ui/components/`
2. Подключить к MainWindow
3. Подписаться на StateFlow для данных

### Добавление плагина
1. Реализовать Plugin interface
2. Положить jar в `plugins/`
3. PluginManager загрузит автоматически

## Performance

- Telnet чтение в IO Dispatcher
- UI в Main Dispatcher
- Парсинг текста оптимизирован (ByteArray вместо String)
- TODO: виртуализация длинного текста в OutputPanel

## Security

- TODO: SSL/TLS поддержка
- TODO: валидация команд перед отправкой
- TODO: sandbox для плагинов
