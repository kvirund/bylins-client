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
 * Панель для просмотра MSDP данных
 */
@Composable
fun MsdpPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val msdpData by clientState.msdpData.collectAsState()
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
            text = "MSDP данные (MUD Server Data Protocol)",
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (msdpData.isEmpty()) {
            Text(
                text = "MSDP данные ещё не получены от сервера.\nПодключитесь к серверу и дождитесь получения данных.",
                color = Color.Gray,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Text(
                text = "Всего переменных: ${msdpData.size}",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Divider(color = Color.Gray, thickness = 1.dp)

            // Группируем переменные по категориям
            val categories = groupMsdpByCategory(msdpData)

            categories.forEach { (category, variables) ->
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
                        // Название категории
                        Text(
                            text = category,
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Переменные в категории
                        variables.forEach { (key, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = key,
                                    color = Color(0xFF90CAF9),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatMsdpValue(value),
                                    color = Color(0xFFBBBBBB),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Группирует MSDP переменные по категориям
 */
private fun groupMsdpByCategory(data: Map<String, Any>): Map<String, List<Pair<String, Any>>> {
    val categories = mutableMapOf<String, MutableList<Pair<String, Any>>>()

    data.forEach { (key, value) ->
        val category = when {
            key.startsWith("HEALTH") || key.startsWith("MANA") || key.startsWith("MOVE") -> "Здоровье и ресурсы"
            key.startsWith("CHAR") || key == "NAME" || key == "LEVEL" || key == "CLASS" || key == "RACE" -> "Персонаж"
            key.startsWith("ROOM") || key == "AREA" || key == "AREA_NAME" -> "Локация"
            key.startsWith("GOLD") || key.startsWith("MONEY") || key == "EXPERIENCE" || key == "EXPERIENCE_TNL" -> "Опыт и золото"
            key.startsWith("AC") || key.startsWith("HITROLL") || key.startsWith("DAMROLL") -> "Характеристики боя"
            key.startsWith("STR") || key.startsWith("INT") || key.startsWith("WIS") ||
            key.startsWith("DEX") || key.startsWith("CON") || key.startsWith("CHA") -> "Статы"
            key.startsWith("AFFECTS") || key.startsWith("BUFF") -> "Аффекты"
            key.startsWith("GROUP") || key.startsWith("PARTY") -> "Группа"
            key.startsWith("OPPONENT") || key.startsWith("TARGET") -> "Противник"
            else -> "Прочее"
        }

        categories.getOrPut(category) { mutableListOf() }.add(key to value)
    }

    // Сортируем категории и переменные внутри них
    return categories.mapValues { (_, list) ->
        list.sortedBy { it.first }
    }.toSortedMap()
}

/**
 * Форматирует значение MSDP для отображения
 */
private fun formatMsdpValue(value: Any): String {
    return when (value) {
        is Map<*, *> -> {
            val entries = value.entries.take(5).joinToString(", ") { "${it.key}=${it.value}" }
            if (value.size > 5) "{$entries, ...}" else "{$entries}"
        }
        is List<*> -> {
            val items = value.take(5).joinToString(", ")
            if (value.size > 5) "[$items, ...]" else "[$items]"
        }
        else -> value.toString()
    }
}
