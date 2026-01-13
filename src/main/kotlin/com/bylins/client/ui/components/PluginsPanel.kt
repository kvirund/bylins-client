package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.plugins.LoadedPlugin
import com.bylins.client.plugins.PluginState
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("PluginsPanel")
@Composable
fun PluginsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    val plugins by clientState.getPlugins().collectAsState()
    val pluginsDirectory = clientState.getPluginsDirectory()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(8.dp)
    ) {
        // Заголовок с кнопками
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Плагины",
                color = colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Открываем диалог выбора JAR файла
                        val fileChooser = JFileChooser(pluginsDirectory)
                        fileChooser.fileFilter = FileNameExtensionFilter(
                            "Plugin files (*.jar)",
                            "jar"
                        )

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val loaded = clientState.loadPlugin(fileChooser.selectedFile)
                            if (loaded != null) {
                                clientState.enablePlugin(loaded.metadata.id)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.primary
                    )
                ) {
                    Text("+ Загрузить", color = Color.White)
                }

                Button(
                    onClick = {
                        // Открываем директорию с плагинами
                        try {
                            val desktop = java.awt.Desktop.getDesktop()
                            desktop.open(File(pluginsDirectory))
                        } catch (e: Exception) {
                            logger.error { "Error opening plugins directory: ${e.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.secondary
                    )
                ) {
                    Text("Открыть папку", color = Color.White)
                }
            }
        }

        // Информация о директории плагинов
        Text(
            text = "Директория: $pluginsDirectory",
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список плагинов
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(plugins) { plugin ->
                PluginItem(
                    plugin = plugin,
                    colorScheme = colorScheme,
                    onToggle = { id, enabled ->
                        if (enabled) {
                            clientState.enablePlugin(id)
                        } else {
                            clientState.disablePlugin(id)
                        }
                    },
                    onReload = { id ->
                        clientState.reloadPlugin(id)
                    },
                    onUnload = { id ->
                        clientState.unloadPlugin(id)
                    }
                )
            }
        }

        if (plugins.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Нет загруженных плагинов",
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Плагины (*.jar) из директории '$pluginsDirectory' загружаются автоматически",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Каждый плагин должен содержать файл plugin.yml с метаданными",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginItem(
    plugin: LoadedPlugin,
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onToggle: (String, Boolean) -> Unit,
    onReload: (String) -> Unit,
    onUnload: (String) -> Unit
) {
    val stateColor = when (plugin.state) {
        PluginState.ENABLED -> Color(0xFF4CAF50) // Green
        PluginState.DISABLED -> Color(0xFF9E9E9E) // Gray
        PluginState.LOADED -> Color(0xFF2196F3) // Blue
        PluginState.ERROR -> Color(0xFFF44336) // Red
    }

    val stateText = when (plugin.state) {
        PluginState.ENABLED -> "Включен"
        PluginState.DISABLED -> "Выключен"
        PluginState.LOADED -> "Загружен"
        PluginState.ERROR -> "Ошибка"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = colorScheme.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Информация о плагине
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = plugin.metadata.name,
                            color = if (plugin.state == PluginState.ENABLED)
                                colorScheme.onSurface
                            else
                                colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "v${plugin.metadata.version}",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // Индикатор статуса
                        Surface(
                            color = stateColor.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stateText,
                                color = stateColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (plugin.metadata.description.isNotEmpty()) {
                        Text(
                            text = plugin.metadata.description,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (plugin.metadata.author.isNotEmpty()) {
                        Text(
                            text = "Автор: ${plugin.metadata.author}",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Кнопки управления
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка вкл/выкл
                    Switch(
                        checked = plugin.state == PluginState.ENABLED,
                        onCheckedChange = { enabled ->
                            onToggle(plugin.metadata.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF81C784),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )

                    // Кнопка перезагрузки
                    Button(
                        onClick = { onReload(plugin.metadata.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.secondary
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("R", fontSize = 14.sp, color = Color.White)
                    }

                    // Кнопка выгрузки
                    Button(
                        onClick = { onUnload(plugin.metadata.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("X", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // Показываем ошибку, если есть
            if (plugin.state == PluginState.ERROR && plugin.errorMessage != null) {
                Text(
                    text = "Ошибка: ${plugin.errorMessage}",
                    color = colorScheme.error,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Показываем зависимости, если есть
            if (plugin.metadata.dependencies.isNotEmpty()) {
                Text(
                    text = "Зависимости: ${plugin.metadata.dependencies.joinToString(", ") { it.id }}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
