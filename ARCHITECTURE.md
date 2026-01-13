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

### 6. TODO: Scripting System (`scripting/`)

Система скриптов с поддержкой Python, Lua, JavaScript:

#### Архитектура движков
```kotlin
interface ScriptEngine {
    val language: String
    fun loadScript(path: String): Script
    fun executeScript(script: Script, context: ScriptContext)
    fun reloadScript(scriptId: String)
}

class ScriptEngineManager {
    private val engines = mapOf(
        "py" to PythonScriptEngine(),     // GraalVM Python / Jython
        "lua" to LuaScriptEngine(),       // LuaJ
        "js" to JavaScriptEngine()        // GraalVM JS / Nashorn
    )

    fun loadScript(path: String): Script {
        val extension = path.substringAfterLast(".")
        return engines[extension]?.loadScript(path)
            ?: throw UnsupportedScriptException(extension)
    }
}

// Единый API доступный из всех языков
interface ScriptAPI {
    fun send(command: String)                    // Отправить команду на сервер
    fun echo(text: String, color: String? = null) // Вывести текст локально
    fun addTrigger(pattern: String, callback: Function)
    fun addAlias(name: String, callback: Function)
    fun getVariable(name: String): Any?
    fun setVariable(name: String, value: Any)
    fun getMsdpValue(key: String): Any?          // Получить MSDP данные
    fun getMapperRoom(): Room?                   // Текущая комната
}
```

#### Примеры скриптов

**Python** (`scripts/auto_heal.py`):
```python
def on_load(api):
    api.echo("Auto-heal script loaded!", "green")
    api.add_trigger(r"У вас осталось (\d+) жизней", on_low_hp)

def on_low_hp(api, match):
    hp = int(match.group(1))
    max_hp = api.get_msdp_value("MAX_HEALTH")
    if hp < max_hp * 0.3:
        api.send("cast 'cure serious'")
        api.echo(f"Auto-healing! HP: {hp}/{max_hp}", "yellow")
```

**Lua** (`scripts/auto_loot.lua`):
```lua
function on_load(api)
    api.echo("Auto-loot script loaded!", "green")
    api.add_trigger("^(.+) мертв", on_kill)
end

function on_kill(api, match)
    local corpse = match[1]
    api.send("взять все " .. corpse)
    api.send("взять все.монета труп")
end
```

**JavaScript** (`scripts/speedwalk.js`):
```javascript
function on_load(api) {
    api.echo("Speedwalk script loaded!", "green");
    api.add_alias("^#(\\d+)([nsewud])$", speedwalk);
}

function speedwalk(api, match) {
    const count = parseInt(match[1]);
    const direction = match[2];

    for (let i = 0; i < count; i++) {
        api.send(direction);
    }
}
```

### 7. TODO: Plugins System (`plugins/`)

Система плагинов с поддержкой Kotlin/Java и Python:

#### Kotlin/Java плагины
```kotlin
interface Plugin {
    val name: String
    val version: String
    val author: String

    fun onLoad(api: PluginAPI)
    fun onUnload()
    fun onCommand(cmd: String): Boolean
    fun onServerOutput(text: String)
}

class PluginManager {
    fun loadPlugin(path: String): Plugin
    fun unloadPlugin(name: String)
    fun reloadPlugin(name: String)
}
```

#### Python плагины
```python
# plugins/damage_counter/plugin.py
class DamageCounterPlugin:
    name = "Damage Counter"
    version = "1.0.0"
    author = "Player"

    def on_load(self, api):
        self.total_damage = 0
        api.add_trigger(r"Вы нанесли (\d+) урона", self.on_damage)

    def on_damage(self, api, match):
        damage = int(match.group(1))
        self.total_damage += damage
        api.set_variable("total_damage", self.total_damage)
```

## Data Flow

```
User Input -> InputPanel -> TelnetClient.send() -> Server

Server -> TelnetClient.receive() -> TelnetParser -> TextProcessor
                                                  -> MSDP Handler -> StatusPanel

TextProcessor:
  -> TriggerManager (execute triggers)
  -> RedirectManager (check redirect rules)
     -> Tab 1 (Main)
     -> Tab 2 (Chat)
     -> Tab 3 (Combat)
     -> Tab N (Custom)
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
