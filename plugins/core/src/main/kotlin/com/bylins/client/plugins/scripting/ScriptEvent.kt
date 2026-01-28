package com.bylins.client.plugins.scripting

/**
 * События для скриптов и плагинов
 */
enum class ScriptEvent {
    ON_LOAD,           // При загрузке скрипта
    ON_UNLOAD,         // При выгрузке скрипта
    ON_COMMAND,        // При отправке команды
    ON_LINE,           // При получении строки от сервера
    ON_CONNECT,        // При подключении
    ON_DISCONNECT,     // При отключении
    ON_MSDP,           // При получении MSDP данных
    ON_MSDP_ENABLED,   // При включении MSDP сервером
    ON_GMCP,           // При получении GMCP данных
    ON_TRIGGER,        // При срабатывании триггера
    ON_ALIAS,          // При срабатывании алиаса
    ON_ROOM_ENTER,     // При входе в новую комнату (маппер)

    // Боевые события для AI-бота
    ON_COMBAT_START,      // Начало боя
    ON_COMBAT_END,        // Конец боя (победа/бегство/смерть)
    ON_DAMAGE_DEALT,      // Нанесён урон
    ON_DAMAGE_RECEIVED,   // Получен урон
    ON_MOB_KILLED,        // Убит моб
    ON_PLAYER_DEATH,      // Смерть персонажа
    ON_AFFECT_APPLIED,    // Наложен эффект
    ON_AFFECT_EXPIRED,    // Эффект истёк
    ON_LEVEL_UP,          // Повышение уровня
    ON_EXP_GAIN,          // Получен опыт
    ON_ITEM_PICKED,       // Подобран предмет
    ON_ZONE_CHANGED,      // Смена зоны

    // События для бота
    ON_LOW_HP,            // HP ниже порога (для flee)
    ON_LOW_MANA,          // Мана ниже порога
    ON_SKILL_READY,       // Навык готов к использованию
    ON_TARGET_CHANGED     // Сменилась цель
}
