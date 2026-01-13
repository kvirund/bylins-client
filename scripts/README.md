# Система скриптов Bylins MUD Client

Клиент поддерживает скрипты на **JavaScript**, **Python** и **Lua**.

## Статус поддержки языков

- **JavaScript** (.js) - Полностью работает (Nashorn встроен в JVM)
- **Python** (.py) - Работает через Jython (Python 2.7 синтаксис)
- **Lua** (.lua) - Работает через LuaJ (Lua 5.2)

## Быстрый старт

1. Поместите скрипты в директорию `scripts/`
2. Скрипты загружаются автоматически при запуске клиента
3. Или перезагрузите скрипт через UI панель

## JavaScript API

### Базовые функции

```javascript
// Отправить команду на сервер
send("look");

// Вывести текст в лог клиента
echo("Hello from script!");
mud_log("Debug message");
print("Info message");
```

### События (хуки)

```javascript
// При загрузке скрипта
function on_load(api) {
    mud_log("Script loaded!");
}

// При выгрузке скрипта
function on_unload() {
    mud_log("Script unloaded!");
}

// При подключении к серверу
function on_connect() {
    send("look");
}

// При отключении
function on_disconnect() {
    mud_log("Disconnected");
}

// При получении строки от сервера
function on_line(line) {
    if (line.includes("мертв")) {
        send("взять все труп");
    }
}

// При отправке команды
function on_command(command) {
    mud_log("Sending: " + command);
}

// При получении MSDP данных
function on_msdp(data) {
    var hp = api.getMsdpValue("HEALTH");
    mud_log("HP: " + hp);
}

// При входе в новую комнату
function on_room_enter(room) {
    mud_log("Entered: " + room.name);
}
```

### Триггеры

```javascript
function on_load(api) {
    // Простой триггер
    addTrigger("^(.+) говорит вам: (.+)", function(line, groups) {
        var who = groups[1];
        var message = groups[2];
        echo("Tell from " + who + ": " + message);
    });

    // Триггер на лут
    addTrigger("^(.+) мертв\\.$", function(line, groups) {
        send("взять все труп");
    });
}
```

### Алиасы

```javascript
function on_load(api) {
    // Простой алиас
    addAlias("^gg$", "say Hi everyone!");

    // Алиас с параметрами (regex $1, $2...)
    addAlias("^k (.+)$", "kill $1");
}
```

### Таймеры

```javascript
function on_load(api) {
    // Одноразовый таймер (5 секунд)
    setTimeout(function() {
        send("look");
    }, 5000);

    // Повторяющийся таймер (каждые 10 секунд)
    var intervalId = setInterval(function() {
        send("score");
    }, 10000);

    // Остановить таймер
    // clearTimer(intervalId);
}
```

### Переменные

```javascript
function on_load(api) {
    // Установить переменную
    setVar("target", "goblin");

    // Получить переменную
    var target = getVar("target");

    // Использовать в командах
    send("kill " + target);
}
```

### MSDP данные

```javascript
function on_msdp(data) {
    var hp = api.getMsdpValue("HEALTH");
    var maxHp = api.getMsdpValue("HEALTH_MAX");
    var mana = api.getMsdpValue("MANA");

    if (hp < maxHp * 0.3 && mana > 50) {
        send("cast 'cure serious'");
    }
}
```

## Python API

Python скрипты используют глобальные функции и объекты-хелперы.

### Базовые функции

```python
# -*- coding: utf-8 -*-

# Отправить команду на сервер
send("look")

# Вывести текст
echo("Hello from script!")
mud_log("Debug message")

# Переменные
set_var("target", "goblin")
target = get_var("target")

# Триггеры
def my_trigger(line, groups):
    echo("Match: " + groups[1])

add_trigger(r"^(.+) мертв\.$", my_trigger)

# Таймеры
def on_timer():
    send("look")

set_timeout(on_timer, 5000)      # Одноразовый
set_interval(on_timer, 10000)    # Повторяющийся
```

### Объекты-хелперы

```python
# MSDP
msdp.get("HEALTH")
msdp.report("STATE")
msdp.is_enabled()

# Статус-панель
status.add_bar("hp", {"label": "HP", "value": 100, "max": 100, "color": "green"})
status.add_text("level", {"label": "Уровень", "value": "50"})
status.update("hp", {"value": 80})

# Маппер
mapper.set_enabled(True)
mapper.handle_room({"vnum": "5001", "name": "Таверна", "exits": {"north": "5002"}})
room = mapper.get_current_room()
```

### События

```python
def on_load(api):
    mud_log("Script loaded")

def on_connect():
    send("look")

def on_msdp(data):
    hp = data.get("HEALTH")
    if hp:
        status.update("hp", {"value": int(hp)})

def on_line(line):
    if "мертв" in line:
        send("взять все труп")
```

## Lua API

Lua скрипты используют глобальные функции и объекты-хелперы.

### Базовые функции

```lua
-- Отправить команду на сервер
send("look")

-- Вывести текст
echo("Hello from script!")
mud_log("Debug message")

-- Переменные
set_var("target", "goblin")
local target = get_var("target")

-- Триггеры
add_trigger("^(.+) мертв%.$", function(line, groups)
    echo("Match: " .. groups[1])
end)

-- Таймеры
set_timeout(function()
    send("look")
end, 5000)

set_interval(function()
    send("score")
end, 10000)
```

### Объекты-хелперы

```lua
-- MSDP
msdp:get("HEALTH")
msdp:report("STATE")
msdp:is_enabled()

-- Статус-панель
status:add_bar("hp", {label = "HP", value = 100, max = 100, color = "green"})
status:add_text("level", {label = "Уровень", value = "50"})
status:update("hp", {value = 80})

-- Маппер
mapper:set_enabled(true)
mapper:handle_room({vnum = "5001", name = "Таверна", exits = {north = "5002"}})
local room = mapper:get_current_room()
```

### События

```lua
function on_load(api)
    mud_log("Script loaded")
end

function on_connect()
    send("look")
end

function on_msdp(data)
    local hp = data:get("HEALTH")
    if hp then
        status:update("hp", {value = tonumber(hp)})
    end
end

function on_line(line)
    if line:find("мертв") then
        send("взять все труп")
    end
end
```

## Отладка

- Используйте `mud_log(message)` для вывода отладочной информации
- Также доступен объектный вызов `api.log(message)` во всех языках
- Ошибки в скриптах выводятся в консоль клиента
- Перезагружайте скрипты после изменений

## Примечания

- Скрипты выполняются в изолированной среде
- JavaScript работает из коробки (Nashorn встроен в JVM)
- Python работает через Jython (включён в зависимости)
- Lua работает через LuaJ (включён в зависимости)
- Скрипты загружаются автоматически при запуске клиента
- Hot reload поддерживается
