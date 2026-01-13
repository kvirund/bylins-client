// Автоматическое лечение при низком HP

// Настройки
var HEAL_THRESHOLD = 30; // Процент HP для лечения
var MANA_REQUIRED = 50;   // Минимум маны для каста
var HEAL_SPELL = "cast 'cure serious'";

function on_load(api) {
    mud_log("🏥 Автохил загружен");
    echo("═══════════════════════════════════════");
    echo("  Автохил активен!");
    echo("  Порог лечения: " + HEAL_THRESHOLD + "%");
    echo("  Минимум маны: " + MANA_REQUIRED);
    echo("═══════════════════════════════════════");
}

function on_unload() {
    mud_log("🏥 Автохил выгружен");
}

function on_msdp(data) {
    var hp = api.getMsdpValue("HEALTH");
    var maxHp = api.getMsdpValue("HEALTH_MAX");
    var mana = api.getMsdpValue("MANA");

    if (hp && maxHp && mana) {
        var hpPercent = (hp / maxHp) * 100;

        if (hpPercent < HEAL_THRESHOLD && mana > MANA_REQUIRED) {
            echo("🏥 Автохил: HP " + Math.round(hpPercent) + "% - лечимся!");
            send(HEAL_SPELL);
        }
    }
}

function on_line(line) {
    // Реагируем на критическое состояние
    if (line.includes("Вы истекаете кровью") || line.includes("Вы при смерти")) {
        echo("⚠️  КРИТИЧЕСКОЕ СОСТОЯНИЕ!");
        send("flee");
    }
}
