package com.bylins.client.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Color mappings for room terrain types in MUD
 */
object TerrainColors {
    private val colors = mapOf(
        "INSIDE" to Color(0xFF808080),       // Gray - indoors
        "CITY" to Color(0xFFCCCCCC),          // Light gray - city
        "FIELD" to Color(0xFF90EE90),         // Light green - field
        "FOREST" to Color(0xFF228B22),        // Green - forest
        "HILLS" to Color(0xFF8B4513),         // Brown - hills
        "MOUNTAIN" to Color(0xFF696969),      // Dark gray - mountains
        "WATER_SWIM" to Color(0xFF87CEEB),    // Sky blue - swimmable water
        "WATER_NOSWIM" to Color(0xFF4169E1),  // Royal blue - non-swimmable water
        "FLYING" to Color(0xFFE0FFFF),        // Light cyan - flying area
        "UNDERWATER" to Color(0xFF000080),    // Navy - underwater
        "SECRET" to Color(0xFF800080),        // Purple - secret area
        "ROAD" to Color(0xFFD2B48C),          // Tan - road
        "DEATH" to Color(0xFF8B0000),         // Dark red - death trap
        "DARK" to Color(0xFF2F2F2F),          // Almost black - darkness
        "WATER_SHALLOW" to Color(0xFFADD8E6), // Light blue - shallow water
        "JUNGLE" to Color(0xFF006400),        // Dark green - jungle
        "SWAMP" to Color(0xFF556B2F),         // Olive - swamp
        "BEACH" to Color(0xFFFAEBD7),         // Antique white - beach
        "DESERT" to Color(0xFFFFD700),        // Gold - desert
        "ICE" to Color(0xFFB0E0E6),           // Powder blue - ice
        "AIR" to Color(0xFFF0FFFF),           // Azure - air
        "LAVA" to Color(0xFFFF4500),          // Orange red - lava
        "CAVE" to Color(0xFF3D3D3D),          // Gray - cave
        "TUNDRA" to Color(0xFFDCDCDC)         // Gainsboro - tundra
    )

    /**
     * Get color for terrain type.
     * Returns null if terrain is unknown.
     */
    fun getColor(terrain: String?): Color? {
        if (terrain == null) return null
        return colors[terrain.uppercase()]
    }

    /**
     * Get all available terrain types
     */
    fun getTerrainTypes(): Set<String> = colors.keys
}
