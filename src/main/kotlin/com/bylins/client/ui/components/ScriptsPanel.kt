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
import com.bylins.client.scripting.Script
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("ScriptsPanel")
@Composable
fun ScriptsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    val scripts by clientState.getScripts().collectAsState()
    val availableEngines = clientState.getAvailableScriptEngines()
    val scriptsDirectory = clientState.getScriptsDirectory()

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
                text = "Скрипты",
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Открываем диалог выбора файла
                        val fileChooser = JFileChooser(scriptsDirectory)
                        fileChooser.fileFilter = FileNameExtensionFilter(
                            "Script files (*.js, *.py, *.lua, *.pl)",
                            "js", "py", "lua", "pl"
                        )

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            clientState.loadScript(fileChooser.selectedFile)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Загрузить", color = colorScheme.onSurface)
                }

                Button(
                    onClick = {
                        // Открываем директорию со скриптами
                        try {
                            val desktop = java.awt.Desktop.getDesktop()
                            desktop.open(File(scriptsDirectory))
                        } catch (e: Exception) {
                            logger.error { "Error opening scripts directory: ${e.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.primary
                    )
                ) {
                    Text("📁 Открыть папку", color = colorScheme.onSurface)
                }
            }
        }

        // Информация о доступных движках
        if (availableEngines.isNotEmpty()) {
            Text(
                text = "Доступные движки: ${availableEngines.joinToString(", ")}",
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список скриптов
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(scripts) { script ->
                ScriptItem(
                    script = script,
                    onToggle = { id, enabled ->
                        if (enabled) {
                            clientState.enableScript(id)
                        } else {
                            clientState.disableScript(id)
                        }
                    },
                    onReload = { id ->
                        clientState.reloadScript(id)
                    },
                    onUnload = { id ->
                        clientState.unloadScript(id)
                    }
                )
            }
        }

        if (scripts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Нет загруженных скриптов",
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Скрипты из директории '$scriptsDirectory' загружаются автоматически",
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
private fun ScriptItem(
    script: Script,
    onToggle: (String, Boolean) -> Unit,
    onReload: (String) -> Unit,
    onUnload: (String) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    val hasFailed = script.hasFailed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = if (hasFailed) colorScheme.error.copy(alpha = 0.1f) else colorScheme.surface,
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
                // Информация о скрипте
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = script.name,
                            color = when {
                                hasFailed -> colorScheme.error
                                script.enabled -> colorScheme.onSurface
                                else -> colorScheme.onSurfaceVariant
                            },
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (hasFailed) {
                            Text(
                                text = "⚠ ОШИБКА",
                                color = colorScheme.error,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "${script.engine.name} | ${script.path}",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Кнопки управления
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка вкл/выкл (для failed скриптов - показываем как выключенный)
                    Switch(
                        checked = script.enabled && !hasFailed,
                        onCheckedChange = { enabled ->
                            onToggle(script.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.success,
                            checkedTrackColor = colorScheme.success.copy(alpha = 0.5f),
                            uncheckedThumbColor = if (hasFailed) colorScheme.error else colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = if (hasFailed) colorScheme.error.copy(alpha = 0.3f) else colorScheme.border
                        )
                    )

                    // Кнопка перезагрузки
                    Button(
                        onClick = { onReload(script.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (hasFailed) colorScheme.warning else colorScheme.primary
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (hasFailed) "🔄 Повторить" else "🔄", fontSize = 14.sp)
                    }

                    // Кнопка выгрузки
                    Button(
                        onClick = { onUnload(script.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("✕", color = colorScheme.onSurface, fontSize = 14.sp)
                    }
                }
            }

            // Показываем ошибку если есть
            if (hasFailed && script.loadError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = script.loadError,
                    color = colorScheme.error,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
