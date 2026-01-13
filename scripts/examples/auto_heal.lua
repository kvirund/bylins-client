-- Автоматическое лечение при низком HP (Lua)

-- Настройки
local HEAL_THRESHOLD = 30  -- Процент HP для лечения
local MANA_REQUIRED = 50   -- Минимум маны для каста
local HEAL_SPELL = "cast 'cure serious'"

function on_load(api)
    mud_log("🏥 Автохил загружен (Lua)")
    echo("═══════════════════════════════════════")
    echo("  Автохил активен! (Lua/LuaJ)")
    echo("  Порог лечения: " .. HEAL_THRESHOLD .. "%")
    echo("  Минимум маны: " .. MANA_REQUIRED)
    echo("═══════════════════════════════════════")
end

function on_unload()
    mud_log("🏥 Автохил выгружен")
end

function on_msdp(data)
    local hp = api:getMsdpValue("HEALTH")
    local max_hp = api:getMsdpValue("HEALTH_MAX")
    local mana = api:getMsdpValue("MANA")

    if hp and max_hp and mana then
        local hp_value = tonumber(hp)
        local max_hp_value = tonumber(max_hp)
        local mana_value = tonumber(mana)

        if hp_value and max_hp_value and mana_value then
            local hp_percent = (hp_value / max_hp_value) * 100

            if hp_percent < HEAL_THRESHOLD and mana_value > MANA_REQUIRED then
                echo(string.format("🏥 Автохил: HP %.0f%% - лечимся!", hp_percent))
                send(HEAL_SPELL)
            end
        end
    end
end

function on_line(line)
    -- Реагируем на критическое состояние
    local line_str = tostring(line)
    if string.find(line_str, "Вы истекаете кровью") or string.find(line_str, "Вы при смерти") then
        echo("⚠️  КРИТИЧЕСКОЕ СОСТОЯНИЕ!")
        send("flee")
    end
end
