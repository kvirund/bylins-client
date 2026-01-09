package com.bylins.client.aliases

data class Alias(
    val id: String,
    val name: String,
    val pattern: Regex,
    val commands: List<String>,
    val enabled: Boolean = true,
    val priority: Int = 0
)

data class AliasMatch(
    val alias: Alias,
    val matchResult: MatchResult,
    val originalCommand: String
)
