# Система скриптов Bylins MUD Client

Клиент поддерживает скрипты на **JavaScript**, **Python**, **Lua** и **Perl**.

## Статус поддержки языков

- **JavaScript** (.js) - ✅ Полностью работает (Nashorn встроен в JVM)
- **Python** (.py) - ✅ Работает через Jython (Python 2.7 синтаксис)
- **Lua** (.lua) - ✅ Работает через LuaJ (Lua 5.2)
- **Perl** (.pl) - ✅ Работает через внешний Perl интерпретатор

## Быстрый старт

1. Поместите `.js` скрипты в директорию `scripts/`
2. Скрипты загружаются автоматически при запуске клиента
3. Или перезагрузите скрипт через UI панель

## JavaScript API

### Базовые функции

```javascript
// Отправить команду на сервер
send("look");

// Вывести текст в лог клиента
echo("Hello from script!");
log("Debug message");
print("Info message");
```

### События (хуки)

```javascript
// При загрузке скрипта
function on_load(api) {
    log("Script loaded!");
}

// При выгрузке скрипта
function on_unload() {
    log("Script unloaded!");
}

// При подключении к серверу
function on_connect() {
    send("look");
}

// При отключении
function on_disconnect() {
    log("Disconnected");
}

// При получении строки от сервера
function on_line(line) {
    if (line.includes("мертв")) {
        send("взять все труп");
    }
}

// При отправке команды
function on_command(command) {
    log("Sending: " + command);
}

// При получении MSDP данных
function on_msdp(data) {
    var hp = api.getMsdpValue("HEALTH");
    log("HP: " + hp);
}

// При входе в новую комнату
function on_room_enter(room) {
    log("Entered: " + room.name);
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

    // Speedwalk: #3n = север три раза
    addAlias("^#(\\d+)([nsewud])$", "");
}

function on_command(command) {
    var match = command.match(/^#(\d+)([nsewud])$/);
    if (match) {
        var count = parseInt(match[1]);
        var dir = match[2];
        for (var i = 0; i < count; i++) {
            send(dir);
        }
        return true; // Блокируем отправку оригинальной команды
    }
    return false;
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

    // Триггер сохраняет цель
    addTrigger("^Вы атакуете (.+)!$", function(line, groups) {
        setVar("current_target", groups[1]);
    });
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

### Автомаппер

```javascript
function on_room_enter(room) {
    var roomData = api.getCurrentRoom();

    log("Вошли в комнату: " + roomData.name + " [" + roomData.id + "]");

    // Установить заметку
    api.setRoomNote(roomData.id, "Здесь водятся гоблины");

    // Покрасить комнату
    api.setRoomColor(roomData.id, "#FF0000");
}
```

## Примеры скриптов

### Пример 1: Автохил

```javascript
// scripts/auto_heal.js
function on_load(api) {
    log("Auto-heal script loaded");
}

function on_msdp(data) {
    var hp = api.getMsdpValue("HEALTH");
    var maxHp = api.getMsdpValue("HEALTH_MAX");
    var mana = api.getMsdpValue("MANA");

    if (hp && maxHp && mana) {
        var hpPercent = (hp / maxHp) * 100;
        if (hpPercent < 30 && mana > 50) {
            send("cast 'cure serious'");
        }
    }
}
```

### Пример 2: Автолут

```javascript
// scripts/auto_loot.js
function on_load(api) {
    addTrigger("^(.+) мертв\\.$", function(line, groups) {
        var mob = groups[1];
        send("взять все труп");
        echo("Собираем лут с: " + mob);
    });
}
```

### Пример 3: Статистика боя

```javascript
// scripts/combat_stats.js
var damageDealt = 0;
var damageReceived = 0;
var killsCount = 0;

function on_load(api) {
    // Урон нанесенный
    addTrigger("^Вы попали по .+ на (\\d+) урона\\.$", function(line, groups) {
        damageDealt += parseInt(groups[1]);
    });

    // Урон полученный
    addTrigger("^.+ попал по вам на (\\d+) урона\\.$", function(line, groups) {
        damageReceived += parseInt(groups[1]);
    });

    // Убийства
    addTrigger("^(.+) мертв\\.$", function(line, groups) {
        killsCount++;
    });

    // Показать статистику
    addAlias("^stats$", "");
}

function on_command(command) {
    if (command === "stats") {
        echo("=== Статистика боя ===");
        echo("Убито мобов: " + killsCount);
        echo("Нанесено урона: " + damageDealt);
        echo("Получено урона: " + damageReceived);
        return true;
    }
    return false;
}
```

### Пример 4: Speedwalk

```javascript
// scripts/speedwalk.js
function on_load(api) {
    addAlias("^#(\\d+)([nsewud]+)$", "");
}

function on_command(command) {
    var match = command.match(/^#(\d+)([nsewud]+)$/);
    if (match) {
        var count = parseInt(match[1]);
        var dirs = match[2];

        for (var i = 0; i < count; i++) {
            for (var j = 0; j < dirs.length; j++) {
                send(dirs[j]);
            }
        }
        return true;
    }
    return false;
}

// Использование: #5n2e = 5 раз север, 2 раза восток
```

## Отладка

- Используйте `log(message)` для вывода отладочной информации
- Ошибки в скриптах выводятся в консоль клиента
- Перезагружайте скрипты после изменений

## Примечания

- Скрипты выполняются в изолированной среде
- JavaScript работает из коробки (Nashorn встроен в JVM)
- Python работает через Jython (включён в зависимости)
- Lua работает через LuaJ (включён в зависимости)
- Perl требует установленный Perl в системе (JSON::PP модуль)
- Скрипты загружаются автоматически при запуске клиента
- Hot reload поддерживается
