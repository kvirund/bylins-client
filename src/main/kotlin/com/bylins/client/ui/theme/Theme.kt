package com.bylins.client.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme as MaterialColorScheme

/**
 * Цветовая схема темы оформления
 */
data class ColorScheme(
    // Основные цвета
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,

    // Цвета текста
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,

    // Цвет вывода текста игры
    val outputBackground: Color,
    val outputText: Color,

    // Акцентные цвета
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,

    // Цвета статуса
    val success: Color,
    val warning: Color,
    val error: Color,

    // Границы и разделители
    val border: Color,
    val divider: Color
)

/**
 * Перечисление доступных тем
 */
enum class ThemeType(val displayName: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    DARK_BLUE("Тёмно-синяя"),
    SOLARIZED_DARK("Solarized Dark"),
    MONOKAI("Monokai")
}

/**
 * Предустановленные темы оформления
 */
object AppTheme {
    /**
     * Тёмная тема (по умолчанию)
     */
    val Dark = ColorScheme(
        background = Color(0xFF1E1E1E),
        surface = Color(0xFF2B2B2B),
        surfaceVariant = Color(0xFF3C3C3C),

        onBackground = Color(0xFFE0E0E0),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFBBBBBB),

        outputBackground = Color(0xFF000000),
        outputText = Color(0xFFBBBBBB),

        primary = Color(0xFF4A9EFF),
        primaryVariant = Color(0xFF2979FF),
        secondary = Color(0xFF00BCD4),

        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),

        border = Color(0xFF555555),
        divider = Color(0xFF444444)
    )

    /**
     * Светлая тема
     */
    val Light = ColorScheme(
        background = Color(0xFFF5F5F5),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE0E0E0),

        onBackground = Color(0xFF212121),
        onSurface = Color(0xFF000000),
        onSurfaceVariant = Color(0xFF424242),

        outputBackground = Color(0xFFFFFFFF),
        outputText = Color(0xFF212121),

        primary = Color(0xFF1976D2),
        primaryVariant = Color(0xFF0D47A1),
        secondary = Color(0xFF00838F),

        success = Color(0xFF388E3C),
        warning = Color(0xFFF57C00),
        error = Color(0xFFD32F2F),

        border = Color(0xFFBDBDBD),
        divider = Color(0xFFE0E0E0)
    )

    /**
     * Тёмно-синяя тема
     */
    val DarkBlue = ColorScheme(
        background = Color(0xFF0D1117),
        surface = Color(0xFF161B22),
        surfaceVariant = Color(0xFF21262D),

        onBackground = Color(0xFFC9D1D9),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF8B949E),

        outputBackground = Color(0xFF000000),
        outputText = Color(0xFFC9D1D9),

        primary = Color(0xFF58A6FF),
        primaryVariant = Color(0xFF1F6FEB),
        secondary = Color(0xFF56D364),

        success = Color(0xFF238636),
        warning = Color(0xFFD29922),
        error = Color(0xFFDA3633),

        border = Color(0xFF30363D),
        divider = Color(0xFF21262D)
    )

    /**
     * Solarized Dark тема
     */
    val SolarizedDark = ColorScheme(
        background = Color(0xFF002B36),
        surface = Color(0xFF073642),
        surfaceVariant = Color(0xFF586E75),

        onBackground = Color(0xFF839496),
        onSurface = Color(0xFFFDF6E3),
        onSurfaceVariant = Color(0xFF93A1A1),

        outputBackground = Color(0xFF002B36),
        outputText = Color(0xFF839496),

        primary = Color(0xFF268BD2),
        primaryVariant = Color(0xFF2AA198),
        secondary = Color(0xFF859900),

        success = Color(0xFF859900),
        warning = Color(0xFFB58900),
        error = Color(0xFFDC322F),

        border = Color(0xFF586E75),
        divider = Color(0xFF073642)
    )

    /**
     * Monokai тема
     */
    val Monokai = ColorScheme(
        background = Color(0xFF272822),
        surface = Color(0xFF3E3D32),
        surfaceVariant = Color(0xFF49483E),

        onBackground = Color(0xFFF8F8F2),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF75715E),

        outputBackground = Color(0xFF1E1E1E),
        outputText = Color(0xFFF8F8F2),

        primary = Color(0xFF66D9EF),
        primaryVariant = Color(0xFFAE81FF),
        secondary = Color(0xFFA6E22E),

        success = Color(0xFFA6E22E),
        warning = Color(0xFFE6DB74),
        error = Color(0xFFF92672),

        border = Color(0xFF75715E),
        divider = Color(0xFF49483E)
    )

    /**
     * Получить цветовую схему по типу темы
     */
    fun getColorScheme(themeType: ThemeType): ColorScheme {
        return when (themeType) {
            ThemeType.DARK -> Dark
            ThemeType.LIGHT -> Light
            ThemeType.DARK_BLUE -> DarkBlue
            ThemeType.SOLARIZED_DARK -> SolarizedDark
            ThemeType.MONOKAI -> Monokai
        }
    }

    /**
     * Получить цветовую схему по строковому имени
     */
    fun getColorScheme(themeName: String): ColorScheme {
        return try {
            val themeType = ThemeType.valueOf(themeName)
            getColorScheme(themeType)
        } catch (e: IllegalArgumentException) {
            Dark // По умолчанию
        }
    }

    /**
     * Конвертирует нашу ColorScheme в Material Design ColorScheme
     */
    fun toMaterialColorScheme(colorScheme: ColorScheme, isDark: Boolean = true): MaterialColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = colorScheme.primary,
                secondary = colorScheme.secondary,
                background = colorScheme.background,
                surface = colorScheme.surface,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = colorScheme.onBackground,
                onSurface = colorScheme.onSurface,
                error = colorScheme.error
            )
        } else {
            lightColorScheme(
                primary = colorScheme.primary,
                secondary = colorScheme.secondary,
                background = colorScheme.background,
                surface = colorScheme.surface,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = colorScheme.onBackground,
                onSurface = colorScheme.onSurface,
                error = colorScheme.error
            )
        }
    }
}

/**
 * CompositionLocal для доступа к кастомной цветовой схеме из компонентов
 */
val LocalAppColorScheme = compositionLocalOf { AppTheme.Dark }
