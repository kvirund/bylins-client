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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.ui.theme.LocalAppColorScheme
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("HotkeysPanel")

@Composable
fun HotkeysPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val hotkeys by clientState.hotkeys.collectAsState()
    val ignoreNumLock by clientState.ignoreNumLock.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHotkey by remember { mutableStateOf<Hotkey?>(null) }
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
                text = "Горячие клавиши",
                color = colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Экспорт JSON
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("hotkeys.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val hotkeyIds = hotkeys.map { it.id }
                                val json = clientState.exportHotkeys(hotkeyIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Hotkeys exported to ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary)
                ) {
                    Text("JSON↓", color = Color.White, fontSize = 11.sp)
                }

                // Импорт JSON
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importHotkeys(json, merge = true)
                                logger.info { "Imported $count hotkeys from ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary)
                ) {
                    Text("JSON↑", color = Color.White, fontSize = 11.sp)
                }

                // Экспорт YAML
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("YAML files", "yaml", "yml")
                        fileChooser.selectedFile = File("hotkeys.yaml")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val yaml = exportHotkeysToYaml(hotkeys)
                                fileChooser.selectedFile.writeText(yaml)
                                logger.info { "Hotkeys exported to YAML: ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "YAML export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.warning)
                ) {
                    Text("YAML↓", color = Color.White, fontSize = 11.sp)
                }

                // Импорт YAML
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("YAML files", "yaml", "yml")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val yamlText = fileChooser.selectedFile.readText()
                                val count = importHotkeysFromYaml(yamlText, clientState)
                                logger.info { "Imported $count hotkeys from YAML: ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "YAML import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.warning)
                ) {
                    Text("YAML↑", color = Color.White, fontSize = 11.sp)
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.success)
                ) {
                    Text("+ Добавить", color = Color.White)
                }
            }
        }

        // Настройка NumLock
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = ignoreNumLock,
                onCheckedChange = { clientState.setIgnoreNumLock(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = colorScheme.success,
                    uncheckedColor = colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = "Игнорировать NumLock (NumPad работает независимо от состояния)",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Диалог добавления хоткея
        if (showAddDialog) {
            HotkeyDialog(
                onDismiss = { showAddDialog = false },
                onSave = { hotkey ->
                    clientState.addHotkey(hotkey)
                    showAddDialog = false
                }
            )
        }

        // Диалог редактирования хоткея
        if (editingHotkey != null) {
            HotkeyDialog(
                hotkey = editingHotkey,
                onDismiss = { editingHotkey = null },
                onSave = { hotkey ->
                    clientState.updateHotkey(hotkey)
                    editingHotkey = null
                }
            )
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Заголовок таблицы
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Комбинация",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(120.dp)
            )
            Text(
                text = "Команды",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            // Место под кнопки (Switch + Edit + Delete)
            Spacer(modifier = Modifier.width(120.dp))
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список хоткеев (отсортированный)
        val sortedHotkeys = remember(hotkeys) {
            hotkeys.sortedWith(
                compareBy<Hotkey> { !it.ctrl && !it.alt && !it.shift } // Сначала с модификаторами
                    .thenBy { it.ctrl }
                    .thenBy { it.alt }
                    .thenBy { it.shift }
                    .thenBy { it.getKeyCombo() }
            )
        }

        if (sortedHotkeys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет горячих клавиш",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sortedHotkeys, key = { it.id }) { hotkey ->
                    HotkeyRow(
                        hotkey = hotkey,
                        onToggle = { id, enabled ->
                            if (enabled) {
                                clientState.enableHotkey(id)
                            } else {
                                clientState.disableHotkey(id)
                            }
                        },
                        onEdit = { editHotkey ->
                            editingHotkey = editHotkey
                        },
                        onDelete = { id ->
                            clientState.removeHotkey(id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HotkeyRow(
    hotkey: Hotkey,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Hotkey) -> Unit,
    onDelete: (String) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Комбинация клавиш
        Text(
            text = hotkey.getKeyCombo(),
            color = if (hotkey.enabled) colorScheme.success else colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(120.dp)
        )

        // Команды
        Text(
            text = hotkey.commands.joinToString("; "),
            color = if (hotkey.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Кнопки управления
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch включения/выключения
            Switch(
                checked = hotkey.enabled,
                onCheckedChange = { enabled ->
                    onToggle(hotkey.id, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.success,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(width = 40.dp, height = 24.dp)
            )

            // Кнопка редактирования
            Button(
                onClick = { onEdit(hotkey) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorScheme.primary
                ),
                modifier = Modifier.size(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "✎",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            // Кнопка удаления
            Button(
                onClick = { onDelete(hotkey.id) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorScheme.error
                ),
                modifier = Modifier.size(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Экспортирует хоткеи в YAML формат
 */
private fun exportHotkeysToYaml(hotkeys: List<Hotkey>): String {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
    }
    val yaml = Yaml(options)

    val data = hotkeys.map { hotkey ->
        mapOf(
            "combo" to hotkey.getKeyCombo(),
            "commands" to hotkey.commands,
            "enabled" to hotkey.enabled
        )
    }

    return yaml.dump(data)
}

/**
 * Импортирует хоткеи из YAML формата
 */
@Suppress("UNCHECKED_CAST")
private fun importHotkeysFromYaml(yamlText: String, clientState: ClientState): Int {
    val yaml = Yaml()
    val data = yaml.load<List<Map<String, Any>>>(yamlText)

    var count = 0
    for (item in data) {
        try {
            val combo = item["combo"] as? String ?: continue
            val commands = (item["commands"] as? List<*>)?.mapNotNull { it?.toString() } ?: continue
            val enabled = item["enabled"] as? Boolean ?: true

            // Парсим комбинацию клавиш
            val parts = combo.split("+")
            var ctrl = false
            var alt = false
            var shift = false
            var keyName = parts.last()

            for (part in parts.dropLast(1)) {
                when (part.uppercase()) {
                    "CTRL" -> ctrl = true
                    "ALT" -> alt = true
                    "SHIFT" -> shift = true
                }
            }

            val key = Hotkey.parseKey(keyName) ?: continue

            val hotkey = Hotkey(
                id = "hk-${java.util.UUID.randomUUID().toString().take(8)}",
                key = key,
                ctrl = ctrl,
                alt = alt,
                shift = shift,
                commands = commands,
                enabled = enabled
            )

            clientState.addHotkey(hotkey)
            count++
        } catch (e: Exception) {
            logger.error { "Failed to import hotkey: ${e.message}" }
        }
    }

    return count
}
