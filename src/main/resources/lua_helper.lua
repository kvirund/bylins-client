-- Lua helper functions for Bylins MUD Client

-- Глобальные функции для удобства
function send(command)
    api:send(command)
end

function echo(text)
    api:echo(text)
end

function log(message)
    api:log(message)
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
