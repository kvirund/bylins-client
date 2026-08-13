#!/usr/bin/env python3
"""
MCP-мост к Bylins MUD Client.

Транслирует вызовы MCP-инструментов в локальный HTTP API плагина ai-control.
Зависимостей нет намеренно: только стандартная библиотека, чтобы мост
запускался где угодно без установки пакетов.

Транспорт MCP — JSON-RPC 2.0 построчно через stdin/stdout.

Переменные окружения:
  BYLINS_AI_URL           адрес плагина (по умолчанию http://127.0.0.1:4747)
  BYLINS_AI_MASTER_TOKEN  мастер-токен (в клиенте: #ai token)
  BYLINS_AI_NAME          имя контекста, под которым агент виден игроку
"""

import json
import os
import sys
import urllib.error
import urllib.request

PROTOCOL_VERSION = "2024-11-05"

BASE_URL = os.environ.get("BYLINS_AI_URL", "http://127.0.0.1:4747").rstrip("/")
MASTER_TOKEN = os.environ.get("BYLINS_AI_MASTER_TOKEN", "")
SESSION_NAME = os.environ.get("BYLINS_AI_NAME", "claude")

# Токен текущей сессии; открывается лениво при первом обращении к игре
_session = {"token": None, "id": None}


# --------------------------------------------------------------------------
# HTTP к плагину
# --------------------------------------------------------------------------

def _http(path, payload=None, headers=None, timeout=60):
    data = json.dumps(payload or {}).encode("utf-8")
    request = urllib.request.Request(BASE_URL + path, data=data, method="POST")
    request.add_header("Content-Type", "application/json; charset=utf-8")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        try:
            message = json.loads(body).get("error", body)
        except json.JSONDecodeError:
            message = body
        raise RuntimeError("Клиент ответил %s: %s" % (error.code, message))
    except urllib.error.URLError as error:
        raise RuntimeError(
            "Не удалось связаться с клиентом (%s). Запущен ли Bylins Client "
            "и выполнена ли команда «#ai start»?" % error.reason
        )


def _master(path, payload=None):
    if not MASTER_TOKEN:
        raise RuntimeError(
            "Не задан BYLINS_AI_MASTER_TOKEN. Получите токен в клиенте командой «#ai token»."
        )
    return _http(path, payload, {"X-Master-Token": MASTER_TOKEN})


def _session_call(path, payload=None, timeout=60):
    _ensure_session()
    return _http(path, payload, {"X-Session-Token": _session["token"]}, timeout=timeout)


def _ensure_session():
    """Открывает контекст при первом обращении и переоткрывает после разрыва."""
    if _session["token"]:
        return
    result = _master("/session/open", {"name": SESSION_NAME})
    _session["token"] = result.get("token")
    _session["id"] = result.get("id")


def _with_reopen(call):
    """Повторяет вызов один раз, если сессия истекла по таймауту простоя."""
    try:
        return call()
    except RuntimeError as error:
        if "Сессия не найдена" in str(error):
            _session["token"] = None
            _session["id"] = None
            return call()
        raise


# --------------------------------------------------------------------------
# Инструменты MCP
# --------------------------------------------------------------------------

TOOLS = [
    {
        "name": "mud_read",
        "description": (
            "Прочитать вывод MUD, появившийся с прошлого чтения этим агентом. "
            "Курсор двигается сам, поэтому повторный вызов возвращает только новое. "
            "Поле missed > 0 означает, что часть строк вытеснена из буфера."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Максимум строк (по умолчанию 200)"}
            },
        },
    },
    {
        "name": "mud_exec",
        "description": (
            "Отправить команды в игру и дождаться ответа. Возвращает вывод, "
            "появившийся после отправки. Требует права записи: если им владеет "
            "другой агент, вызов вернёт ошибку."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "commands": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Команды по порядку, например [\"смотреть\", \"север\"]",
                },
                "timeoutMs": {"type": "integer", "description": "Сколько ждать ответ, мс (по умолчанию 3000)"},
            },
            "required": ["commands"],
        },
    },
    {
        "name": "mud_status",
        "description": "Состояние клиента: подключение, размер журнала вывода, список контекстов агентов.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "mud_client",
        "description": (
            "Управление самим клиентом — то же, что игрок делает кнопками: профили "
            "подключения, соединение, триггеры, алиасы, хоткеи, вкладки, настройки "
            "(тема, шрифт, кодировка) и логирование (куда пишется лог, вкл/выкл). "
            "Требует разрешений, выданных игроком в настройках плагинов.\n"
            "Действия: schema (какие поля принимает каждая сущность, форма scope "
            "и правила пакета — спрашивайте её, а не подбирайте имена полей), "
            "connect, disconnect, connected, profiles, profiles/create, "
            "profiles/update, profiles/delete, profiles/select, triggers, triggers/create, "
            "triggers/update, triggers/delete, aliases, aliases/create, aliases/delete, "
            "hotkeys, hotkeys/create, hotkeys/update, hotkeys/delete, aliases/update, "
            "tabs, tabs/create, tabs/update, tabs/delete, where (текущая комната и зона), "
            "settings, settings/update, logs, logs/start, logs/stop, "
            "msdp (структурированные данные сервера: ROOM, LEVEL, STATE и прочее; "
            "params.vars — выбрать конкретные), "
            "context/rules, context/rules/create, context/rules/update, "
            "context/rules/delete, context/queue, "
            "variables (переменные клиента: ${target}, ${first_attack} и прочее, "
            "на что ссылаются команды правил), variables/set, variables/delete, "
            "characters, characters/create, characters/push, characters/pop, characters/requires.\n"
            "Массовые правки: create/update/delete у триггеров, алиасов, хоткеев и "
            "контекстных правил принимают пакет — params.items (массив объектов) "
            "или params.ids (массив id). При форме ids поля из корня применяются ко "
            "всему списку: {\"ids\":[...],\"profileId\":\"былины\"} переносит всю "
            "пачку разом, повторять поле в каждом объекте не нужно. Пакет отвечает "
            "{batch, total, failed, results}; ошибка одного элемента не отменяет "
            "остальные. Перенос правил между профилями — это update с profileId "
            "(id сохраняется), а не удаление и создание заново."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "action": {"type": "string", "description": "Действие из списка выше"},
                "params": {
                    "type": "object",
                    "description": (
                        "Параметры действия (id, name, pattern, commands, ...). "
                        "Для triggers/create, aliases/create, hotkeys/create можно указать "
                        "profileId — тогда правило попадёт в профиль персонажа и будет "
                        "работать, только пока этот профиль активен. Там же можно задать "
                        "область действия: scope={\"type\":\"zone\",\"zones\":[\"759\"]} "
                        "или {\"type\":\"room\",\"roomIds\":[...]} — правило сработает "
                        "только там. Скоуп задаётся вложенным объектом; плоская форма, "
                        "как в profile.json, не принимается. "
                        "triggers/create принимает once=true — сработать один раз.\n"
                        "Те же поля принимает update, в том числе "
                        "profileId (null — перенести в базовый набор). Неизвестное поле — "
                        "ошибка 400, и на create тоже: молча оно больше не теряется.\n"
                        "Пакет: {\"items\":[{...},{...}]} или {\"ids\":[\"a\",\"b\"]}, "
                        "до 500 элементов за вызов."
                    ),
                },
            },
            "required": ["action"],
        },
    },
    {
        "name": "mud_map",
        "description": (
            "Карта мира: где игрок, что известно о комнатах, как дойти. "
            "Действия чтения: room (текущая комната), get (по id), search (по названию), "
            "path (маршрут до комнаты: направления и список комнат), "
            "nearest (ближайшая комната по свойству или части названия), "
            "properties, zones (список зон), zone (метаданные зоны), "
            "zone/rooms (все комнаты зоны), zone/properties. "
            "Действия записи (требуют разрешения «Управление клиентом»): "
            "room/set (поля комнаты: name, zone, terrain, visited, notes, color — "
            "так помечают посещённой комнату, куда заходить нельзя, и подписывают "
            "комнаты-заготовки; пустая строка очищает поле), note, "
            "property/set, property/remove, zone/note, zone/property/set, highlight, highlight/clear."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "action": {"type": "string", "description": "Действие из списка выше"},
                "params": {
                    "type": "object",
                    "description": (
                        "Параметры: id, roomId, zoneId, query, targetRoomId, nameContains, "
                        "key, value, note. Свойство задаётся парой key/value. "
                        "Зонные действия (zone, zone/rooms, zone/note, zone/properties, "
                        "zone/property/set) требуют zoneId. "
                        "Для room/set — roomId и меняемые поля, например "
                        "{\"roomId\":\"4344\",\"visited\":true,\"name\":\"ДТ\"}"
                    ),
                },
            },
            "required": ["action"],
        },
    },
    {
        "name": "mud_take_lease",
        "description": (
            "Запросить право отправлять команды. Отдаётся, если текущий держатель "
            "молчит; активного агента не перебивает — тогда попросите игрока "
            "передать право командой «#ai take»."
        ),
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "mud_close",
        "description": "Закрыть свой контекст: снимает созданные им триггеры и освобождает право записи.",
        "inputSchema": {"type": "object", "properties": {}},
    },
]


def call_tool(name, args):
    if name == "mud_read":
        limit = int(args.get("limit", 200))
        result = _with_reopen(lambda: _session_call("/output", {"limit": limit}))
        lines = result.get("lines", [])
        text = "\n".join(lines) if lines else "(нового вывода нет)"
        if result.get("missed"):
            text = "[пропущено строк: %s]\n%s" % (result["missed"], text)
        return text

    if name == "mud_exec":
        commands = args.get("commands") or []
        if isinstance(commands, str):
            commands = [commands]
        if not commands:
            raise RuntimeError("Нужен непустой список commands")
        payload = {"commands": commands, "timeoutMs": int(args.get("timeoutMs", 3000))}
        result = _with_reopen(lambda: _session_call("/exec", payload, timeout=120))
        lines = result.get("lines", [])
        return "\n".join(lines) if lines else "(ответа не последовало)"

    if name == "mud_status":
        return json.dumps(_master("/status"), ensure_ascii=False, indent=2)

    if name == "mud_client":
        action = args.get("action")
        if not action:
            raise RuntimeError("Нужно action")
        params = args.get("params") or {}
        result = _with_reopen(lambda: _session_call("/client/" + action.strip("/"), params))
        return json.dumps(result, ensure_ascii=False, indent=2)

    if name == "mud_map":
        action = args.get("action") or "room"
        params = args.get("params") or {}
        result = _with_reopen(lambda: _session_call("/map/" + action.strip("/"), params))
        return json.dumps(result, ensure_ascii=False, indent=2)

    if name == "mud_take_lease":
        result = _with_reopen(lambda: _session_call("/session/lease"))
        if result.get("granted"):
            return "Право отправлять команды получено"
        return "Отказано: право сейчас у «%s» (агент активен)" % (result.get("holder") or "?")

    if name == "mud_close":
        if not _session["token"]:
            return "Контекст и так не открыт"
        result = _session_call("/session/close")
        _session["token"] = None
        _session["id"] = None
        return "Контекст закрыт" if result.get("closed") else "Контекст уже был закрыт"

    raise RuntimeError("Неизвестный инструмент: %s" % name)


# --------------------------------------------------------------------------
# Цикл JSON-RPC
# --------------------------------------------------------------------------

def handle(request):
    method = request.get("method")
    request_id = request.get("id")

    # Уведомления (без id) ответа не требуют
    if request_id is None:
        return None

    if method == "initialize":
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "bylins-mud", "version": "1.0.0"},
        }

    if method == "ping":
        return {}

    if method == "tools/list":
        return {"tools": TOOLS}

    if method == "tools/call":
        params = request.get("params") or {}
        name = params.get("name")
        args = params.get("arguments") or {}
        try:
            text = call_tool(name, args)
            return {"content": [{"type": "text", "text": text}]}
        except Exception as error:  # ошибки инструмента отдаём модели, а не рвём соединение
            return {"content": [{"type": "text", "text": "Ошибка: %s" % error}], "isError": True}

    raise LookupError(method)


def main():
    # На Windows труба по умолчанию получает кодировку локали (cp1252), в
    # которой нет кириллицы: без этого мост падает на первом же русском тексте
    # — хоть в описании инструмента, хоть в выводе игры.
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")

    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            request = json.loads(raw)
        except json.JSONDecodeError:
            continue

        request_id = request.get("id")
        try:
            result = handle(request)
        except LookupError as error:
            response = {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {"code": -32601, "message": "Метод не поддерживается: %s" % error},
            }
        except Exception as error:
            response = {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {"code": -32603, "message": str(error)},
            }
        else:
            if result is None:
                continue
            response = {"jsonrpc": "2.0", "id": request_id, "result": result}

        sys.stdout.write(json.dumps(response, ensure_ascii=False) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
