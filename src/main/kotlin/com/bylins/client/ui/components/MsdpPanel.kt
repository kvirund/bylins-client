package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Панель для просмотра и управления MSDP данными
 */
@Composable
fun MsdpPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    val msdpEnabled by clientState.msdpEnabled.collectAsState()
    val msdpData by clientState.msdpData.collectAsState()
    val reportableVariables by clientState.msdpReportableVariables.collectAsState()
    val reportedVariables by clientState.msdpReportedVariables.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок и статус
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MSDP (MUD Server Data Protocol)",
                color = colorScheme.onSurface,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace
            )

            // Индикатор статуса
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (msdpEnabled) colorScheme.success else colorScheme.error,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Text(
                    text = if (msdpEnabled) "Включён" else "Выключен",
                    color = if (msdpEnabled) colorScheme.success else colorScheme.error,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        if (!msdpEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MSDP не включён",
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Сервер должен отправить WILL MSDP или DO MSDP для включения протокола.\n" +
                            "Подключитесь к серверу, поддерживающему MSDP.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            // Кнопки управления
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surface,
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Управление",
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { clientState.sendMsdpList("COMMANDS") },
                            color = colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "LIST COMMANDS",
                                color = colorScheme.onSurface,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { clientState.sendMsdpList("REPORTABLE_VARIABLES") },
                            color = colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "LIST REPORTABLE",
                                color = colorScheme.onSurface,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { clientState.sendMsdpList("REPORTED_VARIABLES") },
                            color = colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "LIST REPORTED",
                                color = colorScheme.onSurface,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Reportable переменные
            if (reportableVariables.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colorScheme.surface,
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Reportable переменные (${reportableVariables.size})",
                            color = colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Кликните для включения/выключения REPORT",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Divider(color = colorScheme.divider, thickness = 1.dp)

                        // Показываем переменные списком с кнопками
                        reportableVariables.forEach { variable ->
                            val isReported = variable in reportedVariables
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Имя переменной + галочка если reported
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isReported) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colorScheme.success,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = variable,
                                        color = if (isReported) colorScheme.success else colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Кнопки управления
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Кнопка SEND (одноразовый запрос)
                                    Box(
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { clientState.sendMsdpSend(variable) }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Send,
                                            contentDescription = "SEND",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Кнопка REPORT/UNREPORT
                                    Box(
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (isReported) {
                                                    clientState.sendMsdpUnreport(variable)
                                                } else {
                                                    clientState.sendMsdpReport(variable)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isReported) "UNREPORT" else "REPORT",
                                            color = if (isReported) colorScheme.error else colorScheme.success,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Полученные данные
            if (msdpData.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colorScheme.surface,
                    elevation = 2.dp
                ) {
                    Text(
                        text = "Данные MSDP ещё не получены.\nВключите REPORT для интересующих переменных.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colorScheme.surface,
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Полученные данные (${msdpData.size})",
                                color = colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        // Обновить все reported переменные
                                        reportedVariables.forEach { clientState.sendMsdpSend(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Обновить",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Divider(color = colorScheme.divider, thickness = 1.dp)

                        // Группируем переменные по категориям
                        val categories = groupMsdpByCategory(msdpData)

                        categories.forEach { (category, variables) ->
                            Text(
                                text = category,
                                color = colorScheme.success,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )

                            variables.forEach { (key, value) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = key,
                                        color = colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = formatMsdpValue(value),
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
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
            key == "COMMANDS" || key == "LISTS" || key.endsWith("_VARIABLES") -> "Системные"
            else -> "Прочее"
        }

        categories.getOrPut(category) { mutableListOf() }.add(key to value)
    }

    return categories.mapValues { (_, list) ->
        list.sortedBy { it.first }
    }.toSortedMap()
}

/**
 * Форматирует значение MSDP для отображения (без обрезания)
 */
private fun formatMsdpValue(value: Any): String {
    return when (value) {
        is Map<*, *> -> {
            val entries = value.entries.joinToString(", ") { "${it.key}=${formatMsdpValue(it.value ?: "null")}" }
            "{$entries}"
        }
        is List<*> -> {
            val items = value.joinToString(", ") { formatMsdpValue(it ?: "null") }
            "[$items]"
        }
        else -> value.toString()
    }
}
