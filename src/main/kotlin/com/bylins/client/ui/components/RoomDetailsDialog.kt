package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bylins.client.mapper.Room

// Функция для парсинга HEX цвета
private fun parseHexColor(hex: String): Color? {
    return try {
        val cleanHex = hex.removePrefix("#")
        val r = cleanHex.substring(0, 2).toInt(16) / 255f
        val g = cleanHex.substring(2, 4).toInt(16) / 255f
        val b = cleanHex.substring(4, 6).toInt(16) / 255f
        Color(r, g, b)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun RoomDetailsDialog(
    room: Room,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onSaveColor: (String?) -> Unit,
    onSaveTags: (Set<String>) -> Unit,
    onSaveZone: (String) -> Unit
) {
    var note by remember { mutableStateOf(room.notes) }
    var selectedColor by remember { mutableStateOf(room.color) }
    var tags by remember { mutableStateOf(room.tags) }
    var newTag by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(room.zone) }

    val availableColors = listOf(
        null to "Без цвета",
        "#FF0000" to "Красный",
        "#00FF00" to "Зелёный",
        "#0000FF" to "Синий",
        "#FFFF00" to "Жёлтый",
        "#FF00FF" to "Фиолетовый",
        "#00FFFF" to "Бирюзовый",
        "#FFA500" to "Оранжевый",
        "#800080" to "Пурпурный"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Детали комнаты",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                Divider(color = Color.Gray)

                // Информация о комнате
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Название: ${room.name}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ID: ${room.id}",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Координаты: [${room.x}, ${room.y}, ${room.z}]",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Выходы: ${room.getAvailableDirections().joinToString(", ") { it.shortName }}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Divider(color = Color.Gray)

                // Заметка
                Column {
                    Text(
                        text = "Заметка:",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("Добавить заметку...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray
                        )
                    )
                }

                // Цвет комнаты
                Column {
                    Text(
                        text = "Цвет комнаты:",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { (colorCode, colorName) ->
                            val isSelected = selectedColor == colorCode
                            Button(
                                onClick = { selectedColor = colorCode },
                                modifier = Modifier.size(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (colorCode != null) {
                                        parseHexColor(colorCode) ?: Color.Gray
                                    } else {
                                        Color.Gray
                                    },
                                    contentColor = if (isSelected) Color.White else Color.Black
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isSelected) {
                                    Text("✓")
                                }
                            }
                        }
                    }

                    if (selectedColor != null) {
                        Text(
                            text = availableColors.find { it.first == selectedColor }?.second ?: "",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Теги
                Column {
                    Text(
                        text = "Теги (магазин, тренер, квест и т.д.):",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Существующие теги
                    if (tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tags.forEach { tag ->
                                Button(
                                    onClick = { tags = tags - tag },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF424242)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(tag, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("✕", color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Нет тегов",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Добавление нового тега
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Новый тег...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray,
                                unfocusedPlaceholderColor = Color.Gray
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newTag.isNotBlank() && newTag !in tags) {
                                    tags = tags + newTag.trim()
                                    newTag = ""
                                }
                            },
                            enabled = newTag.isNotBlank() && newTag !in tags,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Добавить")
                        }
                    }
                }

                // Зона
                Column {
                    Text(
                        text = "Зона:",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedTextField(
                        value = zone,
                        onValueChange = { zone = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Название зоны...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray
                        ),
                        singleLine = true
                    )
                    Text(
                        text = "Используйте 'Детектировать зоны' для автоматического определения",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Divider(color = Color.Gray)

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Отмена", color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onSaveNote(note)
                            onSaveColor(selectedColor)
                            onSaveTags(tags)
                            onSaveZone(zone)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
