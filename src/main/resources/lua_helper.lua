-- Lua helper functions for Bylins MUD Client

-- Глобальные функции для удобства
function send(command)
    api:send(command)
end

function echo(text)
    api:echo(text)
end

function mud_log(message)
    api:log(tostring(message))
end

-- Переменные
function get_var(name)
    return api:getVariable(name)
end

function set_var(name, value)
    api:setVariable(name, tostring(value))
end

-- Алиасы
function add_alias(pattern, replacement)
    return api:addAlias(pattern, replacement)
end

function clear_timer(timer_id)
    api:clearTimer(timer_id)
end

-- Хелпер для конвертации Lua таблицы в Java коллекции
local HashMap = luajava.bindClass("java.util.HashMap")
local ArrayList = luajava.bindClass("java.util.ArrayList")

local function lua_table_to_java_map(tbl)
    if tbl == nil then return nil end
    local map = HashMap:new()
    for k, v in pairs(tbl) do
        local key = tostring(k)
        local value
        if type(v) == "table" then
            -- Рекурсивно конвертируем вложенные таблицы
            value = lua_table_to_java_map(v)
        elseif type(v) == "nil" then
            value = nil
        else
            value = v
        end
        if value ~= nil then
            map:put(key, value)
        end
    end
    return map
end

-- MSDP
msdp = {
    get = function(key)
        return api:getMsdpValue(key)
    end,
    get_all = function()
        return api:getAllMsdpData()
    end,
    get_reportable = function()
        return api:getMsdpReportableVariables()
    end,
    get_reported = function()
        return api:getMsdpReportedVariables()
    end,
    is_enabled = function()
        return api:isMsdpEnabled()
    end,
    report = function(variable_name)
        api:msdpReport(variable_name)
    end,
    unreport = function(variable_name)
        api:msdpUnreport(variable_name)
    end,
    send = function(variable_name)
        api:msdpSend(variable_name)
    end,
    list = function(list_type)
        api:log("msdp.list called with: " .. tostring(list_type))
        api:msdpList(list_type)
    end,
    is_reportable = function(variable_name)
        local reportable = api:getMsdpReportableVariables()
        if not reportable then return false end
        for i = 1, #reportable do
            if reportable[i] == variable_name then return true end
        end
        return false
    end,
    is_reported = function(variable_name)
        local reported = api:getMsdpReportedVariables()
        if not reported then return false end
        for i = 1, #reported do
            if reported[i] == variable_name then return true end
        end
        return false
    end
}

-- GMCP
function get_gmcp(key)
    return api:getGmcpValue(key)
end

function get_all_gmcp()
    return api:getAllGmcpData()
end

-- Status panel
status = {
    add_bar = function(id, options)
        options = options or {}
        local label = options.label or id
        local value = options.value or 0
        local max = options.max or 100
        local color = options.color or "green"
        local show_text = options.show_text
        if show_text == nil then show_text = true end
        local order = options.order or -1
        api:statusAddBar(id, label, math.floor(value), math.floor(max), color, show_text, math.floor(order))
    end,
    add_text = function(id, options)
        options = options or {}
        local label = options.label or id
        local value = options.value or ""
        local order = options.order or -1
        api:statusAddText(id, label, tostring(value), math.floor(order))
    end,
    add_flags = function(id, options)
        options = options or {}
        local label = options.label or id
        local flags = options.flags or {}
        local order = options.order or -1
        -- Конвертируем Lua таблицу в Java Map
        local java_flags = lua_table_to_java_map(flags)
        api:statusAddFlags(id, label, java_flags, math.floor(order))
    end,
    add_mini_map = function(id, options)
        options = options or {}
        local current_room_id = options.current_room_id
        local visible = options.visible
        if visible == nil then visible = true end
        local order = options.order or -1
        api:statusAddMiniMap(id, current_room_id, visible, math.floor(order))
    end,
    update = function(id, updates)
        -- Конвертируем Lua таблицу в Java Map
        local java_updates = lua_table_to_java_map(updates)
        api:statusUpdate(id, java_updates)
    end,
    remove = function(id)
        api:statusRemove(id)
    end,
    clear = function()
        api:statusClear()
    end,
    get = function(id)
        return api:statusGet(id)
    end,
    exists = function(id)
        return api:statusExists(id)
    end
}

-- Mapper
mapper = {
    get_current_room = function()
        return api:getCurrentRoom()
    end,
    get_room = function(room_id)
        return api:getRoom(room_id)
    end,
    search_rooms = function(query)
        return api:searchRooms(query)
    end,
    find_path = function(target_room_id)
        return api:findPath(target_room_id)
    end,
    set_room_note = function(room_id, note)
        api:setRoomNote(room_id, note)
    end,
    set_room_color = function(room_id, color)
        api:setRoomColor(room_id, color)
    end,
    set_room_zone = function(room_id, zone)
        api:setRoomZone(room_id, zone)
    end,
    set_room_tags = function(room_id, tags)
        -- Конвертируем Lua таблицу в Java List
        local java_tags = ArrayList:new()
        if tags then
            for _, tag in ipairs(tags) do
                java_tags:add(tostring(tag))
            end
        end
        api:setRoomTags(room_id, java_tags)
    end,
    create_room = function(id, name)
        return api:createRoom(id, name)
    end,
    create_room_with_exits = function(id, name, exits)
        -- Конвертируем Lua таблицу в Java Map
        local java_exits = lua_table_to_java_map(exits)
        return api:createRoomWithExits(id, name, java_exits)
    end,
    link_rooms = function(from_room_id, direction, to_room_id)
        api:linkRooms(from_room_id, direction, to_room_id)
    end,
    add_unexplored_exits = function(room_id, exits)
        -- Конвертируем Lua таблицу в Java Map
        local java_exits = lua_table_to_java_map(exits)
        api:addUnexploredExits(room_id, java_exits)
    end,
    handle_movement = function(direction, room_name, exits, room_id)
        -- Конвертируем Lua таблицу в Java Map
        local java_exits = lua_table_to_java_map(exits)
        return api:handleMovement(direction, room_name, java_exits, room_id)
    end,
    handle_room = function(params)
        api:log("mapper.handle_room called")
        -- Конвертируем Lua таблицу в Java Map
        local java_params = lua_table_to_java_map(params)
        local result = api:handleRoom(java_params)
        api:log("mapper.handle_room result: " .. tostring(result))
        return result
    end,
    set_enabled = function(enabled)
        api:setMapEnabled(enabled)
    end,
    is_enabled = function()
        return api:isMapEnabled()
    end,
    clear = function()
        api:clearMap()
    end,
    set_current_room = function(room_id)
        api:setCurrentRoom(room_id)
    end
}
