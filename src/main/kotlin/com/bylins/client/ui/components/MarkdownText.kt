package com.bylins.client.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Simple markdown text renderer without external libraries.
 * Supports:
 * - **bold** text
 * - *italic* text
 * - - bullet lists
 * - Line breaks
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = LocalAppColorScheme.current.onSurface
) {
    val annotated = parseMarkdown(text, color)
    Text(
        text = annotated,
        modifier = modifier,
        style = style
    )
}

/**
 * Parse markdown text into AnnotatedString
 */
private fun parseMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val lines = text.split('\n')

        lines.forEachIndexed { lineIndex, line ->
            // Handle bullet lists
            val trimmedLine = line.trimStart()
            if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
                append("  \u2022 ")
                parseLine(trimmedLine.substring(2), baseColor)
            } else {
                parseLine(line, baseColor)
            }

            // Add newline between lines (not after last)
            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Parse inline formatting within a line
 */
private fun AnnotatedString.Builder.parseLine(line: String, baseColor: Color) {
    var i = 0
    while (i < line.length) {
        when {
            // Bold: **text**
            line.startsWith("**", i) -> {
                val endIndex = line.indexOf("**", i + 2)
                if (endIndex != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(line.substring(i + 2, endIndex))
                    }
                    i = endIndex + 2
                } else {
                    append(line[i])
                    i++
                }
            }
            // Italic: *text* (but not **)
            line.startsWith("*", i) && !line.startsWith("**", i) -> {
                val endIndex = findSingleAsterisk(line, i + 1)
                if (endIndex != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(line.substring(i + 1, endIndex))
                    }
                    i = endIndex + 1
                } else {
                    append(line[i])
                    i++
                }
            }
            // Regular text
            else -> {
                withStyle(SpanStyle(color = baseColor)) {
                    append(line[i])
                }
                i++
            }
        }
    }
}

/**
 * Find closing single asterisk (not double)
 */
private fun findSingleAsterisk(text: String, startIndex: Int): Int {
    var i = startIndex
    while (i < text.length) {
        if (text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*')) {
            return i
        }
        i++
    }
    return -1
}
