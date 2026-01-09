package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.bylins.client.ClientState
import com.bylins.client.ui.AnsiParser

@Composable
fun OutputPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val receivedData by clientState.receivedData.collectAsState()
    val ansiParser = remember { AnsiParser() }

    val outputText: AnnotatedString = remember(receivedData) {
        if (receivedData.isEmpty()) {
            AnnotatedString("Добро пожаловать в Bylins MUD Client!\nПодключитесь к серверу для начала игры.\n\n")
        } else {
            ansiParser.parse(receivedData)
        }
    }

    // Автопрокрутка вниз при появлении нового текста
    LaunchedEffect(receivedData) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
    ) {
        SelectionContainer {
            Text(
                text = outputText,
                color = Color(0xFFCCCCCC),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 18.sp, // Уменьшенный межстрочный интервал
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            )
        }
    }
}
