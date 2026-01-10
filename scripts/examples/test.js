// Тестовый скрипт для проверки работы системы скриптов

function on_load(api) {
    log("✅ Тестовый скрипт загружен!");
    echo("═══════════════════════════════════════");
    echo("  Система скриптов работает!");
    echo("═══════════════════════════════════════");
}

function on_unload() {
    log("❌ Тестовый скрипт выгружен");
}

function on_connect() {
    echo("🔌 Подключились к серверу");
    send("look");
}

function on_disconnect() {
    echo("🔌 Отключились от сервера");
}

function on_line(line) {
    // Логируем каждую 10-ую строку для демонстрации
    if (Math.random() < 0.1) {
        log("📝 Получена строка: " + line.substring(0, 50) + "...");
    }
}

function on_command(command) {
    log("⌨️  Команда отправлена: " + command);
}

function on_msdp(data) {
    var hp = api.getMsdpValue("HEALTH");
    var maxHp = api.getMsdpValue("HEALTH_MAX");
    var mana = api.getMsdpValue("MANA");
    var maxMana = api.getMsdpValue("MANA_MAX");

    if (hp && maxHp && mana && maxMana) {
        log("💊 HP: " + hp + "/" + maxHp + " | Mana: " + mana + "/" + maxMana);
    }
}

function on_trigger(trigger, line, groups) {
    log("🎯 Триггер сработал: " + trigger.name + " на строке: " + line);
}

function on_alias(alias, command, groups) {
    log("⚡ Алиас сработал: " + alias.name + " для команды: " + command);
}

function on_room_enter(room) {
    log("🚪 Вошли в комнату: " + room.name + " [" + room.x + "," + room.y + "," + room.z + "]");
}
