// Speedwalk - быстрое перемещение по направлениям
// Использование: #5n = 5 раз на север
//                #3n2e = 3 раза север, 2 раза восток
//                #5nw = 5 раз на северо-запад

var WALK_DELAY = 200; // Задержка между шагами (мс)

function on_load(api) {
    mud_log("🚶 Speedwalk загружен");
    echo("═══════════════════════════════════════");
    echo("  Speedwalk активен!");
    echo("  Использование: #5n, #3n2e, #10sw");
    echo("  Направления: n,s,e,w,ne,nw,se,sw,u,d");
    echo("  Задержка: " + WALK_DELAY + "мс");
    echo("═══════════════════════════════════════");

    // Регистрируем алиас для speedwalk
    addAlias("^#(.+)$", "");
}

function on_unload() {
    mud_log("🚶 Speedwalk выгружен");
}

function on_command(command) {
    // Проверяем команду speedwalk
    var match = command.match(/^#(.+)$/);
    if (!match) {
        return false;
    }

    var path = match[1];
    var steps = parseSpeedwalk(path);

    if (steps.length === 0) {
        echo("❌ Неверный формат speedwalk: " + command);
        return true;
    }

    echo("🚶 Speedwalk: " + steps.length + " шагов");
    executeSpeedwalk(steps);

    return true;
}

// Парсит строку speedwalk в массив направлений
function parseSpeedwalk(path) {
    var steps = [];
    var i = 0;

    while (i < path.length) {
        // Читаем число
        var numStr = "";
        while (i < path.length && path[i] >= '0' && path[i] <= '9') {
            numStr += path[i];
            i++;
        }

        var count = numStr ? parseInt(numStr) : 1;

        // Читаем направление (может быть 1-2 символа)
        var dir = "";
        if (i < path.length) {
            dir = path[i];
            i++;

            // Проверяем двухбуквенные направления (ne, nw, se, sw)
            if (i < path.length && isValidTwoCharDir(dir + path[i])) {
                dir += path[i];
                i++;
            }
        }

        // Проверяем валидность направления
        if (!isValidDirection(dir)) {
            return []; // Невалидное направление
        }

        // Добавляем шаги
        for (var j = 0; j < count; j++) {
            steps.push(dir);
        }
    }

    return steps;
}

// Проверяет валидность направления
function isValidDirection(dir) {
    var validDirs = ["n", "s", "e", "w", "ne", "nw", "se", "sw", "u", "d"];
    return validDirs.indexOf(dir) !== -1;
}

// Проверяет двухсимвольное направление
function isValidTwoCharDir(dir) {
    return dir === "ne" || dir === "nw" || dir === "se" || dir === "sw";
}

// Выполняет последовательность шагов с задержкой
function executeSpeedwalk(steps) {
    var index = 0;

    function nextStep() {
        if (index < steps.length) {
            send(steps[index]);
            index++;
            setTimeout(nextStep, WALK_DELAY);
        } else {
            echo("🚶 Speedwalk завершён");
        }
    }

    nextStep();
}
