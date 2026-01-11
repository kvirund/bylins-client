# Автоматическое лечение при низком HP (Python)

# Настройки
HEAL_THRESHOLD = 30  # Процент HP для лечения
MANA_REQUIRED = 50   # Минимум маны для каста
HEAL_SPELL = "cast 'cure serious'"

def on_load(api):
    log("🏥 Автохил загружен (Python)")
    echo("═══════════════════════════════════════")
    echo("  Автохил активен! (Python/Jython)")
    echo("  Порог лечения: %d%%" % HEAL_THRESHOLD)
    echo("  Минимум маны: %d" % MANA_REQUIRED)
    echo("═══════════════════════════════════════")

def on_unload():
    log("🏥 Автохил выгружен")

def on_msdp(data):
    hp = api.getMsdpValue("HEALTH")
    max_hp = api.getMsdpValue("HEALTH_MAX")
    mana = api.getMsdpValue("MANA")

    if hp and max_hp and mana:
        try:
            hp_value = float(hp)
            max_hp_value = float(max_hp)
            mana_value = float(mana)

            hp_percent = (hp_value / max_hp_value) * 100

            if hp_percent < HEAL_THRESHOLD and mana_value > MANA_REQUIRED:
                echo("🏥 Автохил: HP %.0f%% - лечимся!" % hp_percent)
                send(HEAL_SPELL)
        except ValueError:
            pass

def on_line(line):
    # Реагируем на критическое состояние
    line_str = str(line)
    if "Вы истекаете кровью" in line_str or "Вы при смерти" in line_str:
        echo("⚠️  КРИТИЧЕСКОЕ СОСТОЯНИЕ!")
        send("flee")
