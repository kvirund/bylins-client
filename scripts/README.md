# Скрипты для Bylins MUD Client

Эта директория для пользовательских скриптов на Python, Lua, JavaScript, или Perl.

## Поддерживаемые языки

- **Python** (`.py`) - через GraalVM Python или Jython
- **Lua** (`.lua`) - через LuaJ
- **JavaScript** (`.js`) - через GraalVM JS
- **Perl** (`.pl`) - через внешний интерпретатор

## Как использовать

1. Положите свой скрипт в эту директорию
2. Скрипт автоматически загрузится при запуске клиента
3. Или используйте команду `/reload` для перезагрузки

## Структура скрипта

Каждый скрипт должен иметь функцию `on_load(api)`:

### Python пример
```python
# scripts/my_script.py
def on_load(api):
    api.echo("Script loaded!", "green")
    api.add_trigger(r"pattern", on_trigger)

def on_trigger(api, match):
    api.send("some command")
```

### Lua пример
```lua
-- scripts/my_script.lua
function on_load(api)
    api.echo("Script loaded!", "green")
    api.add_trigger("pattern", on_trigger)
end

function on_trigger(api, match)
    api.send("some command")
end
```

### JavaScript пример
```javascript
// scripts/my_script.js
function on_load(api) {
    api.echo("Script loaded!", "green");
    api.add_trigger("pattern", on_trigger);
}

function on_trigger(api, match) {
    api.send("some command");
}
```

## API Reference

### api.send(command)
Отправить команду на сервер
```python
api.send("north")
api.send("cast 'cure serious'")
```

### api.echo(text, color?)
Вывести текст в окно клиента
```python
api.echo("Hello, world!")
api.echo("Warning!", "red")
```

### api.add_trigger(pattern, callback)
Добавить триггер на текст от сервера
```python
def on_hp_change(api, match):
    hp = match.group(1)
    api.echo(f"HP changed: {hp}")

api.add_trigger(r"HP: (\d+)", on_hp_change)
```

### api.add_alias(pattern, callback)
Добавить алиас (сокращение команды)
```python
def go_home(api, args):
    api.send("recall")
    api.send("north")
    api.send("enter house")

api.add_alias("^gh$", go_home)
```

### api.get_variable(name) / api.set_variable(name, value)
Глобальные переменные (общие между скриптами)
```python
count = api.get_variable("kill_count") or 0
count += 1
api.set_variable("kill_count", count)
```

### api.get_msdp_value(key)
Получить данные MSDP от сервера
```python
health = api.get_msdp_value("HEALTH")
max_health = api.get_msdp_value("MAX_HEALTH")
room_name = api.get_msdp_value("ROOM_NAME")
```

### api.get_mapper_room()
Получить текущую комнату от автомаппера
```python
room = api.get_mapper_room()
if room:
    api.echo(f"Current room: {room.name}")
```

## Примеры скриптов

### Auto-Heal
```python
# scripts/auto_heal.py
def on_load(api):
    api.add_trigger(r"HP: (\d+)/(\d+)", check_hp)

def check_hp(api, match):
    hp = int(match.group(1))
    max_hp = int(match.group(2))

    if hp < max_hp * 0.3:
        api.send("cast 'cure serious'")
```

### Kill Counter
```python
# scripts/kill_counter.py
def on_load(api):
    api.set_variable("kills", 0)
    api.add_trigger(r"^(.+) мертв", on_kill)
    api.add_alias("^kills$", show_kills)

def on_kill(api, match):
    kills = api.get_variable("kills") or 0
    kills += 1
    api.set_variable("kills", kills)
    api.echo(f"Total kills: {kills}", "yellow")

def show_kills(api, args):
    kills = api.get_variable("kills") or 0
    api.echo(f"You have {kills} kills this session", "green")
```

### Auto-Loot
```lua
-- scripts/auto_loot.lua
function on_load(api)
    api.add_trigger("^(.+) мертв", on_kill)
end

function on_kill(api, match)
    local corpse = match[1]
    api.send("взять все " .. corpse)
    api.send("взять все.монета труп")
end
```

## Зависимости

Для Python скриптов можно использовать `requirements.txt`:
```
# scripts/requirements.txt
requests==2.31.0
pandas==2.0.0
```

Клиент автоматически установит зависимости при первой загрузке.

## Отладка

Используйте `print()` или `api.echo()` для отладки:
```python
def on_trigger(api, match):
    api.echo(f"Debug: matched {match.group(0)}", "cyan")
    # Ваш код
```

## Примечания

- Скрипты выполняются в песочнице (sandbox) для безопасности
- Не используйте блокирующие операции (time.sleep, бесконечные циклы)
- Используйте регулярные выражения для триггеров
- Скрипты можно перезагружать на лету без перезапуска клиента
