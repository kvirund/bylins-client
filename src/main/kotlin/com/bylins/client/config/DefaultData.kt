package com.bylins.client.config

import androidx.compose.ui.input.key.Key
import com.bylins.client.aliases.Alias
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.tabs.CaptureMode
import com.bylins.client.tabs.Tab
import com.bylins.client.tabs.TabFilter
import com.bylins.client.triggers.Trigger
import com.bylins.client.triggers.TriggerColorize

/**
 * Данные по умолчанию для нового клиента (когда конфиг отсутствует)
 * Вынесено из ClientState для уменьшения его размера
 */
object DefaultData {

    /**
     * Алиасы по умолчанию
     */
    fun getDefaultAliases(): List<Alias> = listOf(
        // Алиас для recall (r -> cast 'word of recall')
        Alias(
            id = "recall",
            name = "Recall",
            pattern = "^r$".toRegex(),
            commands = listOf("cast 'word of recall'"),
            enabled = true,
            priority = 10
        ),

        // Алиас для tell (t <name> <text> -> tell <name> <text>)
        Alias(
            id = "tell-short",
            name = "Tell Shortcut",
            pattern = "^t (\\w+) (.+)$".toRegex(),
            commands = listOf("tell $1 $2"),
            enabled = true,
            priority = 10
        ),

        // Алиас для buff (buff -> cast armor, bless, shield)
        Alias(
            id = "buff",
            name = "Buff",
            pattern = "^buff$".toRegex(),
            commands = listOf(
                "cast 'armor'",
                "cast 'bless'",
                "cast 'shield'"
            ),
            enabled = false, // Выключен по умолчанию
            priority = 5
        ),

        // Алиас для cast (c 'spell' target -> cast 'spell' target)
        Alias(
            id = "cast-short",
            name = "Cast Shortcut",
            pattern = "^c '(.+)'( (.+))?$".toRegex(),
            commands = listOf("cast '$1'$2"),
            enabled = true,
            priority = 10
        )
    )

    /**
     * Триггеры по умолчанию
     */
    fun getDefaultTriggers(): List<Trigger> = listOf(
        // Триггер для подсветки tells со звуком
        Trigger(
            id = "tell-notify",
            name = "Tell Notification",
            pattern = "^(.+) говорит вам:".toRegex(),
            commands = listOf("#sound tell"),
            enabled = true,
            priority = 10,
            colorize = TriggerColorize(
                foreground = "#00FF00",
                bold = true
            )
        ),

        // Триггер для подсветки шепота со звуком
        Trigger(
            id = "whisper-notify",
            name = "Whisper Notification",
            pattern = "^(.+) шепчет вам:".toRegex(),
            commands = listOf("#sound whisper"),
            enabled = true,
            priority = 10,
            colorize = TriggerColorize(
                foreground = "#FFFF00",
                bold = true
            )
        ),

        // Пример сохранения переменной из триггера (выключен по умолчанию)
        Trigger(
            id = "capture-target",
            name = "Capture Target",
            pattern = "Вы атакуете (.+)!".toRegex(),
            commands = listOf("#var target $1"),
            enabled = false, // Выключен по умолчанию - пример
            priority = 10
        ),

        // Триггер для gag болталки (выключен по умолчанию)
        Trigger(
            id = "gag-gossip",
            name = "Gag Gossip",
            pattern = "^\\[Болталка\\]".toRegex(),
            commands = emptyList(),
            enabled = false, // Выключен по умолчанию
            priority = 5,
            gag = true
        ),

        // Пример auto-heal триггера (выключен по умолчанию)
        Trigger(
            id = "auto-heal",
            name = "Auto Heal",
            pattern = "HP: (\\d+)/(\\d+)".toRegex(),
            commands = listOf("cast 'cure serious'"),
            enabled = false, // Выключен по умолчанию - опасно!
            priority = 15
        )
    )

    /**
     * Хоткеи по умолчанию
     */
    fun getDefaultHotkeys(): List<Hotkey> = listOf(
        // F1 - info
        Hotkey(
            id = "f1-info",
            key = Key.F1,
            commands = listOf("info")
        ),

        // F2 - score
        Hotkey(
            id = "f2-score",
            key = Key.F2,
            commands = listOf("score")
        ),

        // F3 - inventory
        Hotkey(
            id = "f3-inventory",
            key = Key.F3,
            commands = listOf("inventory")
        ),

        // F4 - look
        Hotkey(
            id = "f4-look",
            key = Key.F4,
            commands = listOf("look")
        )
    )

    /**
     * Вкладки по умолчанию
     */
    fun getDefaultTabs(): List<Tab> = listOf(
        // Вкладка для каналов связи
        Tab(
            id = "channels",
            name = "Каналы",
            filters = listOf(
                TabFilter("^.+ говорит вам:".toRegex()),
                TabFilter("^.+ шепчет вам:".toRegex()),
                TabFilter("^\\[Болталка\\]".toRegex())
            ),
            captureMode = CaptureMode.COPY,
            maxLines = 5000
        )
    )
}
