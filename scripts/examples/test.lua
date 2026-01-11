-- Тестовый скрипт для демонстрации всех событий (Lua)

function on_load(api)
    echo("═══════════════════════════════════════")
    echo("  ✅ Lua тестовый скрипт загружен!")
    echo("═══════════════════════════════════════")
    log("[Lua Test] Скрипт загружен")
end

function on_unload()
    log("[Lua Test] Скрипт выгружен")
end

function on_connect()
    echo("🔌 Lua: Подключение установлено")
    log("[Lua Test] on_connect вызван")
end

function on_disconnect()
    echo("🔌 Lua: Соединение разорвано")
    log("[Lua Test] on_disconnect вызван")
end

function on_line(line)
    -- Просто выводим в лог каждую 100-ю строку
end

function on_command(command)
    log("[Lua Test] Команда отправлена: " .. tostring(command))
end

function on_msdp(data)
    -- Получаем HP и ману
    local hp = api:getMsdpValue("HEALTH")
    if hp then
        log("[Lua Test] HP: " .. tostring(hp))
    end
end

function on_trigger(trigger, line, groups)
    log("[Lua Test] Триггер сработал: " .. tostring(trigger))
end

function on_alias(alias, command, groups)
    log("[Lua Test] Алиас сработал: " .. tostring(alias))
end

function on_room_enter(room)
    log("[Lua Test] Вход в комнату: " .. tostring(room))
end
