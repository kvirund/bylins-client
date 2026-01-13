# Тестовый скрипт для демонстрации всех событий (Python)

def on_load(api):
    echo("═══════════════════════════════════════")
    echo("  ✅ Python тестовый скрипт загружен!")
    echo("═══════════════════════════════════════")
    mud_log("[Python Test] Скрипт загружен")

def on_unload():
    mud_log("[Python Test] Скрипт выгружен")

def on_connect():
    echo("🔌 Python: Подключение установлено")
    mud_log("[Python Test] on_connect вызван")

def on_disconnect():
    echo("🔌 Python: Соединение разорвано")
    mud_log("[Python Test] on_disconnect вызван")

def on_line(line):
    # Просто выводим в лог каждую 100-ю строку
    pass

def on_command(command):
    mud_log("[Python Test] Команда отправлена: %s" % str(command))

def on_msdp(data):
    # Получаем HP и ману
    hp = api.getMsdpValue("HEALTH")
    if hp:
        mud_log("[Python Test] HP: %s" % str(hp))

def on_trigger(trigger, line, groups):
    mud_log("[Python Test] Триггер сработал: %s" % str(trigger))

def on_alias(alias, command, groups):
    mud_log("[Python Test] Алиас сработал: %s" % str(alias))

def on_room_enter(room):
    mud_log("[Python Test] Вход в комнату: %s" % str(room))
