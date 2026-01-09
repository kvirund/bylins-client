package com.bylins.client.triggers

data class Trigger(
    val id: String,
    val name: String,
    val pattern: Regex,
    val commands: List<String>,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val colorize: TriggerColorize? = null,
    val gag: Boolean = false,
    val once: Boolean = false
)

data class TriggerColorize(
    val foreground: String? = null,
    val background: String? = null,
    val bold: Boolean = false
)

sealed class TriggerAction {
    data class SendCommand(val command: String) : TriggerAction()
    data class ExecuteScript(val scriptName: String, val function: String) : TriggerAction()
    data class SetVariable(val name: String, val value: String) : TriggerAction()
    data class PlaySound(val soundFile: String) : TriggerAction()
    object Gag : TriggerAction()
}

data class TriggerMatch(
    val trigger: Trigger,
    val matchResult: MatchResult,
    val line: String
)
