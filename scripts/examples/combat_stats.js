// Статистика боя в реальном времени

var damageDealt = 0;
var damageReceived = 0;
var killsCount = 0;
var deathsCount = 0;
var combatStartTime = null;
var inCombat = false;

function on_load(api) {
    mud_log("📊 Статистика боя загружена");
    echo("═══════════════════════════════════════");
    echo("  Статистика боя активна!");
    echo("  Команды: stats, resetstats");
    echo("═══════════════════════════════════════");

    // Триггеры для отслеживания урона
    addTrigger("^Вы .+ по .+ на (\\d+) урона", function(line, groups) {
        var damage = parseInt(groups[1]);
        damageDealt += damage;

        if (!inCombat) {
            inCombat = true;
            combatStartTime = Date.now();
            echo("⚔️  Бой начался!");
        }
    });

    addTrigger("^.+ .+ по вам на (\\d+) урона", function(line, groups) {
        var damage = parseInt(groups[1]);
        damageReceived += damage;
    });

    addTrigger("^(.+) мертв\\.$", function(line, groups) {
        killsCount++;
        echo("💀 Убийство #" + killsCount);

        if (inCombat) {
            inCombat = false;
            var duration = Math.round((Date.now() - combatStartTime) / 1000);
            echo("⚔️  Бой окончен (" + duration + "с)");
        }
    });

    addTrigger("^Вы мертвы", function(line, groups) {
        deathsCount++;
        echo("💀 Вы погибли (смерть #" + deathsCount + ")");
        inCombat = false;
    });

    // Алиасы для просмотра и сброса статистики
    addAlias("^stats$", "");
    addAlias("^resetstats$", "");
}

function on_unload() {
    mud_log("📊 Статистика боя выгружена");
}

function on_command(command) {
    if (command === "stats") {
        showStats();
        return true;
    }

    if (command === "resetstats") {
        resetStats();
        return true;
    }

    return false;
}

function showStats() {
    echo("═══════════════════════════════════════");
    echo("       📊 СТАТИСТИКА БОЕВЫХ ДЕЙСТВИЙ");
    echo("═══════════════════════════════════════");
    echo("  Убито мобов:      " + killsCount);
    echo("  Смертей:          " + deathsCount);
    echo("  Нанесено урона:   " + damageDealt);
    echo("  Получено урона:   " + damageReceived);

    if (killsCount > 0) {
        var avgDamage = Math.round(damageDealt / killsCount);
        echo("  Средний урон/моб: " + avgDamage);
    }

    if (damageDealt > 0 && damageReceived > 0) {
        var ratio = (damageDealt / damageReceived).toFixed(2);
        echo("  Соотношение:      " + ratio + ":1");
    }

    echo("═══════════════════════════════════════");
}

function resetStats() {
    damageDealt = 0;
    damageReceived = 0;
    killsCount = 0;
    deathsCount = 0;
    echo("📊 Статистика сброшена");
}
