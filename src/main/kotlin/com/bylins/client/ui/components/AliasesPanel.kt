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
import com.bylins.client.aliases.Alias
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("AliasesPanel")
@Composable
fun AliasesPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val aliases by clientState.aliases.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingAlias by remember { mutableStateOf<Alias?>(null) }
    val colorScheme = LocalAppColorScheme.current

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
                text = "Алиасы",
                color = colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        // Экспорт алиасов
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("aliases.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val aliasIds = aliases.map { it.id }
                                val json = clientState.exportAliases(aliasIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Aliases exported to ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.primary
                    )
                ) {
                    Text("Экспорт", color = Color.White)
                }

                Button(
                    onClick = {
                        // Импорт алиасов
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importAliases(json, merge = true)
                                logger.info { "Imported $count aliases from ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.warning
                    )
                ) {
                    Text("Импорт", color = Color.White)
                }

                Button(
                    onClick = {
                        editingAlias = null
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Добавить", color = Color.White)
                }
            }
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список алиасов
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(aliases) { alias ->
                AliasItem(
                    alias = alias,
                    onToggle = { id, enabled ->
                        if (enabled) {
                            clientState.enableAlias(id)
                        } else {
                            clientState.disableAlias(id)
                        }
                    },
                    onEdit = { alias ->
                        editingAlias = alias
                        showDialog = true
                    },
                    onDelete = { id ->
                        clientState.removeAlias(id)
                    }
                )
            }
        }

        if (aliases.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет алиасов",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Диалог добавления/редактирования
    if (showDialog) {
        AliasDialog(
            alias = editingAlias,
            onDismiss = {
                showDialog = false
                editingAlias = null
            },
            onSave = { alias ->
                if (editingAlias == null) {
                    clientState.addAlias(alias)
                } else {
                    clientState.updateAlias(alias)
                }
                showDialog = false
                editingAlias = null
            }
        )
    }
}

@Composable
private fun AliasItem(
    alias: Alias,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Alias) -> Unit,
    onDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = LocalAppColorScheme.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = colorScheme.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Основная строка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Название и ID
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alias.name,
                        color = if (alias.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ID: ${alias.id}",
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
                    // Кнопка включения/выключения
                    Switch(
                        checked = alias.enabled,
                        onCheckedChange = { enabled ->
                            onToggle(alias.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.success,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant
                        )
                    )

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(alias) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.primary
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✎",
                            color = colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }

                    // Кнопка информации
                    Button(
                        onClick = { expanded = !expanded },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = colorScheme.onSurface,
                            fontSize = 10.sp
                        )
                    }

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(alias.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
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

            // Развернутая информация
            if (expanded) {
                Divider(
                    color = colorScheme.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pattern
                    InfoRow("Pattern:", alias.pattern.pattern)

                    // Commands
                    if (alias.commands.isNotEmpty()) {
                        InfoRow("Commands:", alias.commands.joinToString("; "))
                    }

                    // Priority
                    InfoRow("Priority:", alias.priority.toString())
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colorScheme = LocalAppColorScheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
