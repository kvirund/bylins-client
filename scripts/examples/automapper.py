# -*- coding: utf-8 -*-
# Автомаппер для Bylins MUD (Python/Jython версия)
#
# Парсит:
#   Название комнаты: "Постоялый двор [5001]"
#   Выходы в промпте: "Вых:СЮЗВv^>" или "Вых:(С)В(Ю)>"
#

# Состояние
pending_room_id = None
pending_room_name = None
pending_exits = None
last_direction = None

# Маппинг символов выходов
exit_chars = {
    u"С": "north", u"с": "north",
    u"Ю": "south", u"ю": "south",
    u"З": "west",  u"з": "west",
    u"В": "east",  u"в": "east",
    "^": "up",
    "v": "down"
}

# Маппинг направлений
directions = {
    u"север": "north",
    u"юг": "south",
    u"запад": "west",
    u"восток": "east",
    u"вверх": "up",
    u"вниз": "down"
}

def is_debug():
    val = api.getVariable("automapper_debug")
    return val == "true" or val == "1"

def debug(msg):
    if is_debug():
        api.echo("[Mapper DEBUG] " + msg)

def try_create_room():
    global pending_room_id, pending_room_name, pending_exits, last_direction

    api.log("[PY] try_create_room: room_id=" + str(pending_room_id) +
            " room_name=" + str(pending_room_name) +
            " exits=" + str(len(pending_exits) if pending_exits else "nil") +
            " direction=" + str(last_direction))

    if not pending_room_id:
        debug("try_create_room: no room_id")
        return
    if not pending_room_name:
        debug("try_create_room: no room_name")
        return
    if not pending_exits:
        debug("try_create_room: no exits")
        return

    room_id = pending_room_id
    room_name = pending_room_name
    direction = last_direction

    # Вычисляем зону
    try:
        zone_num = int(room_id)
        zone_id = zone_num // 100
    except:
        zone_id = 0

    if direction:
        # Движение - создаём связь
        api.log("[PY] handle_movement: dir=" + direction + " exits=" + ",".join(pending_exits) + " roomId=" + room_id)
        room_info = api.handleMovement(direction, room_name, pending_exits, room_id)
        if room_info:
            api.setRoomZone(room_id, "zone_" + str(zone_id))
            api.echo("[Mapper] " + room_name + " [" + room_id + "]")
        else:
            debug("handleMovement returned None")
    else:
        # Начальная комната
        api.log("[PY] Initial room case")
        current = api.getCurrentRoom()
        current_id = None
        if current:
            try:
                current_id = current.get("id")
            except:
                current_id = None

        api.log("[PY] current_id=" + str(current_id) + " room_id=" + str(room_id))

        if current_id != room_id:
            rooms = api.searchRooms(room_id)
            rooms_count = len(rooms) if rooms else 0
            api.log("[PY] search_rooms(" + room_id + ") found " + str(rooms_count))

            if rooms_count == 0:
                api.log("[PY] Creating new room: " + room_id)
                if api.createRoom(room_id, room_name, 0, 0, 0):
                    api.setRoomZone(room_id, "zone_" + str(zone_id))
                    api.setCurrentRoom(room_id)
                    if pending_exits and len(pending_exits) > 0:
                        api.addUnexploredExits(room_id, pending_exits)
                        api.log("[PY] Added exits: " + ",".join(pending_exits))
                    api.echo("[Mapper] New: " + room_name + " [" + room_id + "]")
                else:
                    api.log("[PY] create_room returned false")
            else:
                api.log("[PY] Room exists, setting current")
                api.setCurrentRoom(room_id)
                if pending_exits and len(pending_exits) > 0:
                    api.addUnexploredExits(room_id, pending_exits)
                    api.log("[PY] Updated exits: " + ",".join(pending_exits))
        else:
            api.log("[PY] current_id == room_id, skipping")

    # Сброс
    pending_room_id = None
    pending_room_name = None
    pending_exits = None
    last_direction = None


# Триггер: название комнаты "Постоялый двор [5001]"
def on_room_trigger(line, groups):
    global pending_room_id, pending_room_name

    api.log("[PY] room callback, groups[1]=" + str(groups.get(1)) + " groups[2]=" + str(groups.get(2)))

    name = groups.get(1)
    room_id = groups.get(2)

    if not name or not room_id:
        return

    # Игнорируем строки карты
    trimmed = name.strip()
    if not trimmed:
        return
    first = trimmed[0]
    if first in [":", "|", "-", " "]:
        return

    pending_room_name = trimmed
    pending_room_id = room_id
    try_create_room()

add_trigger(r"(.+)\[(\d+)\]", on_room_trigger)


# Триггер: выходы "Вых:СЮv^>" или "Вых:(С)В(Ю)>"
def on_exits_trigger(line, groups):
    global pending_exits

    exits_str = groups.get(1)
    api.log("[PY] exits callback, groups[1]=" + repr(exits_str))

    if not exits_str:
        return

    exits = []
    in_brackets = False

    # Простая итерация по Unicode символам
    for char in exits_str:
        if char == "(":
            in_brackets = True
        elif char == ")":
            in_brackets = False
        else:
            direction = exit_chars.get(char)
            if direction and direction not in exits:
                exits.append(direction)

    debug("exits: " + ",".join(exits))
    pending_exits = exits
    try_create_room()

add_trigger(u"Вых:([\\(СЮЗВсюзвv^\\)]+)>", on_exits_trigger)


# Триггер: движение "Вы пошли на север"
def on_movement_trigger(line, groups):
    global last_direction, pending_room_name, pending_room_id, pending_exits

    dir_text = groups.get(1)
    api.log("[PY] movement callback, dir=" + str(dir_text))

    if dir_text:
        direction = directions.get(dir_text.lower())
        if direction:
            last_direction = direction
            pending_room_name = None
            pending_room_id = None
            pending_exits = None

add_trigger(u"Вы \\S+ .*(север|юг|запад|восток|вверх|вниз)", on_movement_trigger)


# При загрузке
def on_load(api_ref):
    if not api.getVariable("automapper_debug"):
        api.setVariable("automapper_debug", "false")

    api.setMapEnabled(True)
    api.echo("[Automapper PY] Script loaded")
    api.echo("[Automapper PY] Debug: #vars automapper_debug=true/false")
