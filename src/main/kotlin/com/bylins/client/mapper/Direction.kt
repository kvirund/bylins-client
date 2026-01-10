package com.bylins.client.mapper

/**
 * Направления движения в MUD
 */
enum class Direction(
    val shortName: String,
    val russianName: String,
    val commands: List<String>,
    val opposite: String,
    val dx: Int = 0,
    val dy: Int = 0,
    val dz: Int = 0
) {
    NORTH("n", "север", listOf("north", "n", "север", "с"), "south", dx = 0, dy = -1),
    SOUTH("s", "юг", listOf("south", "s", "юг", "ю"), "north", dx = 0, dy = 1),
    EAST("e", "восток", listOf("east", "e", "восток", "в"), "west", dx = 1, dy = 0),
    WEST("w", "запад", listOf("west", "w", "запад", "з"), "east", dx = -1, dy = 0),
    NORTHEAST("ne", "северо-восток", listOf("northeast", "ne", "северо-восток", "св"), "southwest", dx = 1, dy = -1),
    NORTHWEST("nw", "северо-запад", listOf("northwest", "nw", "северо-запад", "сз"), "southeast", dx = -1, dy = -1),
    SOUTHEAST("se", "юго-восток", listOf("southeast", "se", "юго-восток", "юв"), "northwest", dx = 1, dy = 1),
    SOUTHWEST("sw", "юго-запад", listOf("southwest", "sw", "юго-запад", "юз"), "northeast", dx = -1, dy = 1),
    UP("u", "вверх", listOf("up", "u", "вверх", "вв"), "down", dz = 1),
    DOWN("d", "вниз", listOf("down", "d", "вниз", "вн"), "up", dz = -1);

    /**
     * Получает противоположное направление
     */
    fun getOpposite(): Direction {
        return values().first { it.name.lowercase() == opposite.lowercase() }
    }

    companion object {
        /**
         * Парсит направление из команды
         */
        fun fromCommand(command: String): Direction? {
            val cmd = command.trim().lowercase()
            return values().firstOrNull { dir ->
                dir.commands.any { it.lowercase() == cmd }
            }
        }

        /**
         * Парсит направление из текста выходов
         */
        fun fromExitText(text: String): List<Direction> {
            val exits = mutableListOf<Direction>()
            val lowerText = text.lowercase()

            for (dir in values()) {
                if (dir.commands.any { lowerText.contains(it) }) {
                    exits.add(dir)
                }
            }

            return exits
        }
    }
}
