// Автоматический сбор лута с убитых мобов

var LOOT_DELAY = 500; // Задержка перед лутом (мс)
var AUTO_SACRIFICE = true; // Автоматически жертвовать труп

function on_load(api) {
    log("💰 Автолут загружен");
    echo("═══════════════════════════════════════");
    echo("  Автолут активен!");
    echo("  Задержка: " + LOOT_DELAY + "мс");
    echo("  Автожертва: " + (AUTO_SACRIFICE ? "ДА" : "НЕТ"));
    echo("═══════════════════════════════════════");

    // Триггер на смерть моба
    addTrigger("^(.+) мертв\\.$", function(line, groups) {
        var mobName = groups[1];
        echo("💀 " + mobName + " убит!");

        // Лутаем с небольшой задержкой
        setTimeout(function() {
            send("взять все труп");

            if (AUTO_SACRIFICE) {
                setTimeout(function() {
                    send("жертвовать труп");
                }, LOOT_DELAY);
            }
        }, LOOT_DELAY);
    });
}

function on_unload() {
    log("💰 Автолут выгружен");
}

// Подсчитываем собранные предметы
var itemsLooted = 0;

function on_line(line) {
    if (line.includes("Вы взяли")) {
        itemsLooted++;
        log("💰 Собрано предметов: " + itemsLooted);
    }

    if (line.includes("В трупе ничего не оказалось")) {
        echo("💰 Труп был пуст");
    }
}
