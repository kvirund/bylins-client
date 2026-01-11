package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun SettingsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusColor by remember { mutableStateOf(Color.White) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            text = "Настройки",
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider(color = Color.Gray, thickness = 1.dp)

        // Логирование
        val isLogging by clientState.isLogging.collectAsState()
        val currentLogFile by clientState.currentLogFile.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Логирование",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = Color.Gray, thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Статус:",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = if (isLogging) "Активно" else "Остановлено",
                        color = if (isLogging) Color(0xFF00FF00) else Color(0xFFFF5555),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (currentLogFile != null) {
                    Text(
                        text = "Текущий файл:",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = currentLogFile!!,
                        color = Color(0xFF00FF00),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "Директория логов: ${clientState.getLogsDirectory()}",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isLogging) {
                                clientState.stopLogging()
                                statusMessage = "Логирование остановлено"
                                statusColor = Color(0xFFFFAA00)
                            } else {
                                clientState.startLogging(stripAnsi = true)
                                statusMessage = "Логирование начато"
                                statusColor = Color(0xFF00FF00)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isLogging) Color(0xFFFF5555) else Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = if (isLogging) "Остановить" else "Начать",
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            try {
                                val logsDir = clientState.getLogsDirectory()
                                val os = System.getProperty("os.name").lowercase()
                                when {
                                    os.contains("win") -> {
                                        Runtime.getRuntime().exec(arrayOf("explorer", logsDir))
                                    }
                                    os.contains("mac") -> {
                                        Runtime.getRuntime().exec(arrayOf("open", logsDir))
                                    }
                                    else -> {
                                        Runtime.getRuntime().exec(arrayOf("xdg-open", logsDir))
                                    }
                                }
                                statusMessage = "Открыта директория логов"
                                statusColor = Color(0xFF00FF00)
                            } catch (e: Exception) {
                                statusMessage = "Ошибка открытия: ${e.message}"
                                statusColor = Color(0xFFFF5555)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("Открыть папку", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val count = clientState.getLogFiles().size
                            clientState.cleanOldLogs(30)
                            val newCount = clientState.getLogFiles().size
                            val removed = count - newCount
                            statusMessage = "Удалено старых логов: $removed"
                            statusColor = Color(0xFF00FF00)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text("Очистить старые", color = Color.White)
                    }
                }

                Text(
                    text = "Всего лог-файлов: ${clientState.getLogFiles().size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Настройка ширины миникарты
        val miniMapWidth by clientState.miniMapWidth.collectAsState()
        var widthInput by remember { mutableStateOf(miniMapWidth.toString()) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Мини-карта",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = Color.Gray, thickness = 1.dp)

                Text(
                    text = "Ширина боковой панели (150-500 dp):",
                    color = Color(0xFFBBBBBB),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = { widthInput = it },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    Button(
                        onClick = {
                            try {
                                val width = widthInput.toInt()
                                clientState.setMiniMapWidth(width)
                                statusMessage = "Ширина миникарты установлена: $width dp"
                                statusColor = Color(0xFF00FF00)
                            } catch (e: NumberFormatException) {
                                statusMessage = "Ошибка: введите число от 150 до 500"
                                statusColor = Color(0xFFFF5555)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Применить", color = Color.White)
                    }

                    Button(
                        onClick = {
                            clientState.setMiniMapWidth(250)
                            widthInput = "250"
                            statusMessage = "Ширина миникарты сброшена до 250 dp"
                            statusColor = Color(0xFF00FF00)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("Сброс", color = Color.White)
                    }
                }

                Text(
                    text = "Текущая ширина: $miniMapWidth dp",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Обновляем widthInput при изменении miniMapWidth
                LaunchedEffect(miniMapWidth) {
                    widthInput = miniMapWidth.toString()
                }
            }
        }

        // Информация о конфигурации
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Конфигурация",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = Color.Gray, thickness = 1.dp)

                Text(
                    text = "Путь к конфигу:",
                    color = Color(0xFFBBBBBB),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = clientState.getConfigPath(),
                    color = Color(0xFF00FF00),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                val triggers by clientState.triggers.collectAsState()
                val aliases by clientState.aliases.collectAsState()
                val hotkeys by clientState.hotkeys.collectAsState()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Триггеров: ${triggers.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Алиасов: ${aliases.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Горячих клавиш: ${hotkeys.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Конфигурация сохраняется автоматически при каждом изменении.",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Экспорт конфигурации
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Экспорт конфигурации",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = Color.Gray, thickness = 1.dp)

                Text(
                    text = "Сохранить триггеры и алиасы в файл.",
                    color = Color(0xFFBBBBBB),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.dialogTitle = "Экспорт конфигурации"
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON файлы", "json")
                        fileChooser.selectedFile = File("bylins-config.json")

                        val result = fileChooser.showSaveDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            try {
                                val file = fileChooser.selectedFile
                                val finalFile = if (!file.name.endsWith(".json")) {
                                    File(file.parentFile, file.name + ".json")
                                } else {
                                    file
                                }

                                clientState.exportConfig(finalFile)
                                statusMessage = "Конфигурация экспортирована: ${finalFile.absolutePath}"
                                statusColor = Color(0xFF00FF00)
                            } catch (e: Exception) {
                                statusMessage = "Ошибка экспорта: ${e.message}"
                                statusColor = Color(0xFFFF5555)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Экспортировать", color = Color.White)
                }
            }
        }

        // Импорт конфигурации
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Импорт конфигурации",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = Color.Gray, thickness = 1.dp)

                Text(
                    text = "Загрузить триггеры и алиасы из файла. ВНИМАНИЕ: текущие настройки будут заменены!",
                    color = Color(0xFFFFAA00),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.dialogTitle = "Импорт конфигурации"
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON файлы", "json")

                        val result = fileChooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            try {
                                val file = fileChooser.selectedFile
                                clientState.importConfig(file)
                                statusMessage = "Конфигурация импортирована из: ${file.absolutePath}"
                                statusColor = Color(0xFF00FF00)
                            } catch (e: Exception) {
                                statusMessage = "Ошибка импорта: ${e.message}"
                                statusColor = Color(0xFFFF5555)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Импортировать", color = Color.White)
                }
            }
        }

        // Статусное сообщение
        if (statusMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF2D2D2D),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage!!,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { statusMessage = null },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF424242)
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
