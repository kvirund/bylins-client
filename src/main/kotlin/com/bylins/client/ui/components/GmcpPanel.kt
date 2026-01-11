package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState

/**
 * Панель для просмотра GMCP данных
 */
@Composable
fun GmcpPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val gmcpData by clientState.gmcpData.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок
        Text(
            text = "GMCP данные (Generic MUD Communication Protocol)",
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (gmcpData.isEmpty()) {
            Text(
                text = "GMCP данные ещё не получены от сервера.\nПодключитесь к серверу и дождитесь получения данных.",
                color = Color.Gray,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Text(
                text = "Всего пакетов: ${gmcpData.size}",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Divider(color = Color.Gray, thickness = 1.dp)

            // Отображаем все GMCP пакеты
            gmcpData.forEach { (packageName, jsonData) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2D2D2D)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Название пакета
                        Text(
                            text = packageName,
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // JSON данные (форматированные)
                        Text(
                            text = formatJson(jsonData.toString()),
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Простое форматирование JSON для читаемости
 */
private fun formatJson(json: String): String {
    if (json.length < 100) return json

    // Простое форматирование - добавляем переносы строк и отступы
    var formatted = json
    formatted = formatted.replace(",", ",\n  ")
    formatted = formatted.replace("{", "{\n  ")
    formatted = formatted.replace("}", "\n}")
    formatted = formatted.replace("[", "[\n  ")
    formatted = formatted.replace("]", "\n]")

    return formatted
}
