// Автомаппер для Bylins MUD (JavaScript/Nashorn версия)
//
// Парсит:
//   Название комнаты: "Постоялый двор [5001]"
//   Выходы в промпте: "Вых:СЮЗВv^>" или "Вых:(С)В(Ю)>"
//

// Состояние
var pendingRoomId = null;
var pendingRoomName = null;
var pendingExits = null;
var lastDirection = null;

// Маппинг символов выходов
var exitChars = {
    "С": "north", "с": "north",
    "Ю": "south", "ю": "south",
    "З": "west",  "з": "west",
    "В": "east",  "в": "east",
    "^": "up",
    "v": "down"
};

// Маппинг направлений
var directions = {
    "север": "north",
    "юг": "south",
    "запад": "west",
    "восток": "east",
    "вверх": "up",
    "вниз": "down"
};

function isDebug() {
    var val = api.getVariable("automapper_debug");
    return val === "true" || val === "1";
}

function debug(msg) {
    if (isDebug()) {
        api.echo("[Mapper DEBUG] " + msg);
    }
}

// UTF-8 итератор для JavaScript
function utf8Chars(str) {
    var chars = [];
    for (var i = 0; i < str.length; i++) {
        var code = str.charCodeAt(i);
        // JavaScript strings are UTF-16, so surrogate pairs need handling
        // but for Cyrillic (BMP) this is simple
        chars.push(str.charAt(i));
    }
    return chars;
}

function tryCreateRoom() {
    api.log("[JS] try_create_room: room_id=" + pendingRoomId +
            " room_name=" + pendingRoomName +
            " exits=" + (pendingExits ? pendingExits.length : "nil") +
            " direction=" + lastDirection);

    if (!pendingRoomId) {
        debug("try_create_room: no room_id");
        return;
    }
    if (!pendingRoomName) {
        debug("try_create_room: no room_name");
        return;
    }
    if (!pendingExits) {
        debug("try_create_room: no exits");
        return;
    }

    var roomId = pendingRoomId;
    var roomName = pendingRoomName;
    var direction = lastDirection;

    // Вычисляем зону
    var zoneNum = parseInt(roomId, 10);
    var zoneId = isNaN(zoneNum) ? 0 : Math.floor(zoneNum / 100);

    if (direction) {
        // Движение - создаём связь
        api.log("[JS] handle_movement: dir=" + direction + " exits=" + pendingExits.join(",") + " roomId=" + roomId);
        var roomInfo = api.handleMovement(direction, roomName, pendingExits, roomId);
        if (roomInfo) {
            api.setRoomZone(roomId, "zone_" + zoneId);
            api.echo("[Mapper] " + roomName + " [" + roomId + "]");
        } else {
            debug("handleMovement returned null");
        }
    } else {
        // Начальная комната
        api.log("[JS] Initial room case");
        var current = api.getCurrentRoom();
        var currentId = null;
        if (current) {
            try {
                currentId = current.get("id");
            } catch (e) {
                currentId = null;
            }
        }

        api.log("[JS] current_id=" + currentId + " room_id=" + roomId);

        if (currentId !== roomId) {
            var rooms = api.searchRooms(roomId);
            var roomsCount = rooms ? rooms.size() : 0;
            api.log("[JS] search_rooms(" + roomId + ") found " + roomsCount);

            if (roomsCount === 0) {
                api.log("[JS] Creating new room: " + roomId);
                if (api.createRoom(roomId, roomName, 0, 0, 0)) {
                    api.setRoomZone(roomId, "zone_" + zoneId);
                    api.setCurrentRoom(roomId);
                    if (pendingExits && pendingExits.length > 0) {
                        api.addUnexploredExits(roomId, pendingExits);
                        api.log("[JS] Added exits: " + pendingExits.join(","));
                    }
                    api.echo("[Mapper] New: " + roomName + " [" + roomId + "]");
                } else {
                    api.log("[JS] create_room returned false");
                }
            } else {
                api.log("[JS] Room exists, setting current");
                api.setCurrentRoom(roomId);
                if (pendingExits && pendingExits.length > 0) {
                    api.addUnexploredExits(roomId, pendingExits);
                    api.log("[JS] Updated exits: " + pendingExits.join(","));
                }
            }
        } else {
            api.log("[JS] current_id == room_id, skipping");
        }
    }

    // Сброс
    pendingRoomId = null;
    pendingRoomName = null;
    pendingExits = null;
    lastDirection = null;
}


// Триггер: название комнаты "Постоялый двор [5001]"
addTrigger("(.+)\\[(\\d+)\\]", function(line, groups) {
    api.log("[JS] room callback, groups[1]=" + groups.get(1) + " groups[2]=" + groups.get(2));

    var name = groups.get(1);
    var roomId = groups.get(2);

    if (!name || !roomId) {
        return;
    }

    // Игнорируем строки карты
    var trimmed = name.trim();
    if (!trimmed || trimmed.length === 0) {
        return;
    }
    var first = trimmed.charAt(0);
    if (first === ":" || first === "|" || first === "-" || first === " ") {
        return;
    }

    pendingRoomName = trimmed;
    pendingRoomId = roomId;
    tryCreateRoom();
});


// Триггер: выходы "Вых:СЮv^>" или "Вых:(С)В(Ю)>"
addTrigger("Вых:([\\(СЮЗВсюзвv^\\)]+)>", function(line, groups) {
    var exitsStr = groups.get(1);
    api.log("[JS] exits callback, groups[1]=" + exitsStr);

    if (!exitsStr) {
        return;
    }

    var exits = [];
    var exitSet = {};
    var inBrackets = false;

    // Итерация по символам (JavaScript strings handle UTF-16)
    var chars = utf8Chars(exitsStr);
    for (var i = 0; i < chars.length; i++) {
        var char = chars[i];

        if (char === "(") {
            inBrackets = true;
        } else if (char === ")") {
            inBrackets = false;
        } else {
            var dir = exitChars[char];
            if (dir && !exitSet[dir]) {
                exits.push(dir);
                exitSet[dir] = true;
            }
        }
    }

    debug("exits: " + exits.join(","));
    pendingExits = exits;
    tryCreateRoom();
});


// Триггер: движение "Вы пошли на север"
addTrigger("Вы \\S+ .*(север|юг|запад|восток|вверх|вниз)", function(line, groups) {
    var dirText = groups.get(1);
    api.log("[JS] movement callback, dir=" + dirText);

    if (dirText) {
        var dir = directions[dirText.toLowerCase()];
        if (dir) {
            lastDirection = dir;
            pendingRoomName = null;
            pendingRoomId = null;
            pendingExits = null;
        }
    }
});


// При загрузке
(function() {
    if (!api.getVariable("automapper_debug")) {
        api.setVariable("automapper_debug", "false");
    }

    api.setMapEnabled(true);
    api.echo("[Automapper JS] Script loaded");
    api.echo("[Automapper JS] Debug: #vars automapper_debug=true/false");
})();
