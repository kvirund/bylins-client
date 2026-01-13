-- Автомаппер для Bylins MUD
--
-- Парсит:
--   Название комнаты: "Постоялый двор [5001]"
--   Выходы в промпте: "Вых:СЮЗВv^>" или "Вых:(С)В(Ю)>"
--     В скобках - закрытые двери, например (С) = север закрыт
--
-- Автоматически включается при загрузке скрипта.
--
-- Управление отладкой:
--   #vars automapper_debug=true   - включить
--   #vars automapper_debug=false  - выключить

local function is_debug()
    local val = get_var("automapper_debug")
    return val == "true" or val == "1"
end

local function debug(msg)
    if is_debug() then
        echo("[Mapper DEBUG] " .. msg)
    end
end

-- UTF-8 итератор: возвращает символы (не байты!)
local function utf8_chars(s)
    local i = 1
    local len = #s
    return function()
        if i > len then return nil end
        local c = s:byte(i)
        local char_len = 1
        if c >= 0xC0 and c < 0xE0 then
            char_len = 2  -- 2-байтовый UTF-8 (кириллица)
        elseif c >= 0xE0 and c < 0xF0 then
            char_len = 3  -- 3-байтовый UTF-8
        elseif c >= 0xF0 then
            char_len = 4  -- 4-байтовый UTF-8
        end
        local char = s:sub(i, i + char_len - 1)
        i = i + char_len
        return char
    end
end

-- Состояние
local pending_room_id = nil
local pending_room_name = nil
local pending_exits = nil
local last_direction = nil

-- Маппинг символов выходов
local exit_chars = {
    ["С"] = "north", ["с"] = "north",
    ["Ю"] = "south", ["ю"] = "south",
    ["З"] = "west",  ["з"] = "west",
    ["В"] = "east",  ["в"] = "east",
    ["^"] = "up",
    ["v"] = "down"
}

-- Маппинг направлений
local directions = {
    ["север"] = "north",
    ["юг"] = "south",
    ["запад"] = "west",
    ["восток"] = "east",
    ["вверх"] = "up",
    ["вниз"] = "down"
}

-- Попытка создать комнату
local function try_create_room()
    -- FORCE DEBUG
    mud_log("[LUA] try_create_room: room_id=" .. tostring(pending_room_id) ..
          " room_name=" .. tostring(pending_room_name) ..
          " exits=" .. tostring(pending_exits and #pending_exits or "nil") ..
          " direction=" .. tostring(last_direction))

    if not pending_room_id then
        debug("try_create_room: нет room_id")
        return
    end
    if not pending_room_name then
        debug("try_create_room: нет room_name")
        return
    end
    if not pending_exits then
        debug("try_create_room: нет exits")
        return
    end

    local room_id = pending_room_id
    local room_name = pending_room_name
    local direction = last_direction

    debug("try_create_room: id=" .. room_id .. " name=" .. room_name .. " dir=" .. tostring(direction))

    -- Вычисляем зону
    local zone_num = tonumber(room_id)
    local zone_id = zone_num and math.floor(zone_num / 100) or 0

    if direction then
        -- Движение - создаём связь
        debug("handle_movement: dir=" .. direction .. " exits=" .. table.concat(pending_exits, ",") .. " roomId=" .. room_id)
        -- Используем нативную функцию handle_movement с игровым ID комнаты
        local room_info = handle_movement(direction, room_name, pending_exits, room_id)
        if room_info then
            api:setRoomZone(room_id, "zone_" .. zone_id)
            echo("[Mapper] " .. room_name .. " [" .. room_id .. "]")
        else
            debug("handle_movement вернул nil")
        end
    else
        -- Начальная комната
        mud_log("[LUA] Initial room case")
        local current = api:getCurrentRoom()
        local current_id = current and (current.id or current:get("id")) or nil
        mud_log("[LUA] current_id=" .. tostring(current_id) .. " room_id=" .. tostring(room_id))

        if current_id ~= room_id then
            -- Используем нативную функцию search_rooms
            local rooms = search_rooms(room_id)
            local rooms_count = rooms and #rooms or 0
            mud_log("[LUA] search_rooms(" .. room_id .. ") found " .. rooms_count)

            if rooms_count == 0 then
                mud_log("[LUA] Creating new room: " .. room_id)
                -- Используем нативную функцию create_room
                if create_room(room_id, room_name, 0, 0, 0) then
                    api:setRoomZone(room_id, "zone_" .. zone_id)
                    api:setCurrentRoom(room_id)
                    -- Добавляем выходы к начальной комнате
                    if pending_exits and #pending_exits > 0 then
                        add_unexplored_exits(room_id, pending_exits)
                        mud_log("[LUA] Added exits: " .. table.concat(pending_exits, ","))
                    end
                    echo("[Mapper] Новая: " .. room_name .. " [" .. room_id .. "]")
                else
                    mud_log("[LUA] create_room returned false")
                end
            else
                mud_log("[LUA] Room exists, setting current")
                api:setCurrentRoom(room_id)
                -- Обновляем выходы для существующей комнаты
                if pending_exits and #pending_exits > 0 then
                    add_unexplored_exits(room_id, pending_exits)
                    mud_log("[LUA] Updated exits: " .. table.concat(pending_exits, ","))
                end
            end
        else
            mud_log("[LUA] current_id == room_id, skipping")
        end
    end

    -- Сброс
    pending_room_id = nil
    pending_room_name = nil
    pending_exits = nil
    last_direction = nil
end

-- Триггер: название комнаты "Постоялый двор [5001]"
-- Java regex: (.+)\[(\d+)\]
add_trigger("(.+)\\[(\\d+)\\]", function(line, groups)
    -- FORCE DEBUG - проверяем вызов callback
    mud_log("[LUA] room callback, groups[1]=" .. tostring(groups[1]) .. " groups[2]=" .. tostring(groups[2]))

    debug("Триггер комнаты: line=" .. line)

    -- groups - это LuaTable с числовыми индексами (0 = полное совпадение, 1+ = группы)
    local name = groups[1]
    local id = groups[2]

    debug("  name=" .. tostring(name) .. " id=" .. tostring(id))

    if not name or not id then return end

    -- Игнорируем строки карты
    local trimmed = name:match("^%s*(.-)%s*$")
    if not trimmed or #trimmed == 0 then
        debug("  пустое имя после trim")
        return
    end
    local first = trimmed:sub(1,1)
    if first == ":" or first == "|" or first == "-" or first == " " then
        debug("  игнорирую (карта): first=" .. first)
        return
    end

    pending_room_name = trimmed
    pending_room_id = id
    debug("  pending: name=" .. trimmed .. " id=" .. id)
    try_create_room()
end)

-- Триггер: выходы "Вых:СЮv^>" или "Вых:(С)В(Ю)>" (в скобках - закрытые двери)
-- Java regex: Вых:([\(СЮЗВсюзвv^\)]+)>
add_trigger("Вых:([\\(СЮЗВсюзвv^\\)]+)>", function(line, groups)
    -- FORCE DEBUG
    mud_log("[LUA] exits callback, groups[1]=" .. tostring(groups[1]))

    debug("Триггер выходов: line=" .. line)

    local exits_str = groups[1]
    debug("  exits_str=" .. tostring(exits_str))

    if not exits_str then return end

    local exits = {}
    local closed_exits = {}
    local in_brackets = false

    -- Используем UTF-8 итератор для корректной обработки кириллицы
    for char in utf8_chars(exits_str) do
        debug("    char='" .. char .. "' bytes=" .. #char)

        if char == "(" then
            in_brackets = true
        elseif char == ")" then
            in_brackets = false
        else
            local dir = exit_chars[char]
            if dir and not exits[dir] then
                table.insert(exits, dir)
                exits[dir] = true
                if in_brackets then
                    closed_exits[dir] = true
                end
            end
        end
    end

    -- Убираем маркеры дубликатов, оставляем только список
    for k, _ in pairs(exits) do
        if type(k) == "string" then exits[k] = nil end
    end

    -- Логируем закрытые выходы отдельно
    local closed_list = {}
    for dir, _ in pairs(closed_exits) do
        table.insert(closed_list, dir)
    end
    if #closed_list > 0 then
        debug("  exits: " .. table.concat(exits, ",") .. " (closed: " .. table.concat(closed_list, ",") .. ")")
    else
        debug("  exits: " .. table.concat(exits, ","))
    end

    pending_exits = exits
    try_create_room()
end)

-- Триггер: движение "Вы пошли на север"
-- Java regex: Вы \S+ .*(север|юг|запад|восток|вверх|вниз)
add_trigger("Вы \\S+ .*(север|юг|запад|восток|вверх|вниз)", function(line, groups)
    debug("Триггер движения: line=" .. line)

    local dir_text = groups[1]
    debug("  dir_text=" .. tostring(dir_text))

    if dir_text then
        local dir = directions[dir_text:lower()]
        if dir then
            debug("  direction=" .. dir)
            last_direction = dir
            pending_room_name = nil
            pending_room_id = nil
            pending_exits = nil
        end
    end
end)

-- При загрузке
function on_load()
    -- Устанавливаем DEBUG по умолчанию
    if not get_var("automapper_debug") then
        set_var("automapper_debug", "true")
    end

    api:setMapEnabled(true)
    echo("[Automapper] Скрипт загружен")
    echo("[Automapper] Отладка: #vars automapper_debug=true/false")
end
