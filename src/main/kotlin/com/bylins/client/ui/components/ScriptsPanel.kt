package com.bylins.client.ui.components

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
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun ScriptsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val scripts by clientState.getScripts().collectAsState()
    val availableEngines = clientState.getAvailableScriptEngines()
    val scriptsDirectory = clientState.getScriptsDirectory()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
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
                color = Color.White,
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
                        backgroundColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("+ Загрузить", color = Color.White)
                }

                Button(
                    onClick = {
                        // Открываем директорию со скриптами
                        try {
                            val desktop = java.awt.Desktop.getDesktop()
                            desktop.open(File(scriptsDirectory))
                        } catch (e: Exception) {
                            println("Error opening scripts directory: ${e.message}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("📁 Открыть папку", color = Color.White)
                }
            }
        }

        // Информация о доступных движках
        if (availableEngines.isNotEmpty()) {
            Text(
                text = "Доступные движки: ${availableEngines.joinToString(", ")}",
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Divider(color = Color.Gray, thickness = 1.dp)

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
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Скрипты из директории '$scriptsDirectory' загружаются автоматически",
                        color = Color.Gray,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = Color(0xFF2D2D2D),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Информация о скрипте
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = script.name,
                    color = if (script.enabled) Color.White else Color.Gray,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${script.engine.name} | ${script.path}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Кнопки управления
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка вкл/выкл
                Switch(
                    checked = script.enabled,
                    onCheckedChange = { enabled ->
                        onToggle(script.id, enabled)
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
                    onClick = { onReload(script.id) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2196F3)
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("🔄", fontSize = 14.sp)
                }

                // Кнопка выгрузки
                Button(
                    onClick = { onUnload(script.id) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
