package com.bylins.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

class AnsiParser {
    private val ESC = '\u001B'

    // ANSI 16 базовых цветов (палитра tintin++)
    private val ansi16Colors = mapOf(
        30 to Color(0xFF555555), // Black (темно-серый, чтобы видеть на чёрном фоне) (85,85,85)
        31 to Color(0xFFBB0000), // Red (187,0,0)
        32 to Color(0xFF00BB00), // Green (0,187,0)
        33 to Color(0xFFBBBB00), // Yellow (187,187,0)
        34 to Color(0xFF0000BB), // Blue (0,0,187)
        35 to Color(0xFFBB00BB), // Magenta (187,0,187)
        36 to Color(0xFF00BBBB), // Cyan (0,187,187)
        37 to Color(0xFFBBBBBB), // White (187,187,187)

        // Bright colors
        90 to Color(0xFF888888), // Bright Black (Gray) (136,136,136)
        91 to Color(0xFFFF5555), // Bright Red (255,85,85)
        92 to Color(0xFF55FF55), // Bright Green (85,255,85)
        93 to Color(0xFFFFFF55), // Bright Yellow (255,255,85)
        94 to Color(0xFF5555FF), // Bright Blue (85,85,255)
        95 to Color(0xFFFF55FF), // Bright Magenta (255,85,255)
        96 to Color(0xFF55FFFF), // Bright Cyan (85,255,255)
        97 to Color(0xFFFFFFFF), // Bright White (255,255,255)
    )

    // ANSI 256 color palette (упрощённая версия)
    private fun get256Color(code: Int): Color {
        return when (code) {
            in 0..15 -> ansi16Colors[code + 30] ?: Color.White
            in 16..231 -> {
                // 216 color cube (6x6x6)
                val idx = code - 16
                val r = ((idx / 36) % 6) * 51
                val g = ((idx / 6) % 6) * 51
                val b = (idx % 6) * 51
                Color(r, g, b)
            }
            in 232..255 -> {
                // Grayscale
                val gray = 8 + (code - 232) * 10
                Color(gray, gray, gray)
            }
            else -> Color.White
        }
    }

    fun parse(text: String): AnnotatedString = buildAnnotatedString {
        var currentPos = 0
        var currentFgColor: Color? = null
        var currentBgColor: Color? = null
        var isBold = false
        var isItalic = false
        var isUnderline = false

        while (currentPos < text.length) {
            val escPos = text.indexOf(ESC, currentPos)

            if (escPos == -1) {
                // Нет больше escape последовательностей
                append(text.substring(currentPos))
                break
            }

            // Добавляем текст до escape последовательности
            if (escPos > currentPos) {
                val span = text.substring(currentPos, escPos)
                pushStyle(createSpanStyle(currentFgColor, currentBgColor, isBold, isItalic, isUnderline))
                append(span)
                pop()
            }

            // Парсим escape последовательность
            if (escPos + 1 < text.length && text[escPos + 1] == '[') {
                val mPos = text.indexOf('m', escPos + 2)
                if (mPos != -1) {
                    val codes = text.substring(escPos + 2, mPos)
                        .split(';')
                        .mapNotNull { it.toIntOrNull() }

                    var i = 0
                    while (i < codes.size) {
                        val code = codes[i]
                        when (code) {
                            0 -> {
                                // Reset
                                currentFgColor = null
                                currentBgColor = null
                                isBold = false
                                isItalic = false
                                isUnderline = false
                            }
                            1 -> isBold = true
                            3 -> isItalic = true
                            4 -> isUnderline = true
                            22 -> isBold = false
                            23 -> isItalic = false
                            24 -> isUnderline = false
                            in 30..37, in 90..97 -> {
                                // Foreground color
                                currentFgColor = ansi16Colors[code]
                            }
                            38 -> {
                                // Extended foreground color
                                if (i + 1 < codes.size) {
                                    when (codes[i + 1]) {
                                        5 -> {
                                            // 256 colors
                                            if (i + 2 < codes.size) {
                                                currentFgColor = get256Color(codes[i + 2])
                                                i += 2
                                            }
                                        }
                                        2 -> {
                                            // RGB
                                            if (i + 4 < codes.size) {
                                                currentFgColor = Color(
                                                    codes[i + 2],
                                                    codes[i + 3],
                                                    codes[i + 4]
                                                )
                                                i += 4
                                            }
                                        }
                                    }
                                }
                            }
                            in 40..47, in 100..107 -> {
                                // Background color
                                currentBgColor = ansi16Colors[code - 10]
                            }
                            48 -> {
                                // Extended background color
                                if (i + 1 < codes.size) {
                                    when (codes[i + 1]) {
                                        5 -> {
                                            // 256 colors
                                            if (i + 2 < codes.size) {
                                                currentBgColor = get256Color(codes[i + 2])
                                                i += 2
                                            }
                                        }
                                        2 -> {
                                            // RGB
                                            if (i + 4 < codes.size) {
                                                currentBgColor = Color(
                                                    codes[i + 2],
                                                    codes[i + 3],
                                                    codes[i + 4]
                                                )
                                                i += 4
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        i++
                    }

                    currentPos = mPos + 1
                } else {
                    // Неполная escape последовательность
                    currentPos = escPos + 1
                }
            } else {
                // Неизвестная escape последовательность
                currentPos = escPos + 1
            }
        }
    }

    private fun createSpanStyle(
        fgColor: Color?,
        bgColor: Color?,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean
    ): SpanStyle {
        return SpanStyle(
            color = fgColor ?: Color.Unspecified,
            background = bgColor ?: Color.Unspecified,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else null
        )
    }
}
