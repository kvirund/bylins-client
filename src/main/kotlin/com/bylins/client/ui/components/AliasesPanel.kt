package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    // Получаем все алиасы с источниками
    val aliasesWithSource = remember { mutableStateOf(clientState.getAllAliasesWithSource()) }
    val aliases by clientState.aliases.collectAsState()
    val activeStack by clientState.profileManager.activeStack.collectAsState()
    val profiles by clientState.profileManager.profiles.collectAsState()

    // Обновляем при изменении алиасов, стека или профилей
    LaunchedEffect(aliases, activeStack, profiles) {
        aliasesWithSource.value = clientState.getAllAliasesWithSource()
    }

    var showDialog by remember { mutableStateOf(false) }
    var editingAlias by remember { mutableStateOf<Alias?>(null) }
    var editingAliasSource by remember { mutableStateOf<String?>(null) }
    var lastTargetProfileId by remember { mutableStateOf<String?>(null) }  // Запоминаем последний выбор
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
                        editingAliasSource = null
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

        // Список алиасов (все из базы + профилей)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(aliasesWithSource.value) { (alias, source) ->
                // Формируем список доступных целей для перемещения (исключая текущий источник)
                val availableTargets = mutableListOf<Pair<String?, String>>()
                if (source != null) {
                    availableTargets.add(null to "База")
                }
                activeStack.forEach { profileId ->
                    if (profileId != source) {
                        profiles.find { it.id == profileId }?.let { profile ->
                            availableTargets.add(profileId to profile.name)
                        }
                    }
                }

                AliasItem(
                    alias = alias,
                    source = source,
                    sourceName = source?.let { srcId -> profiles.find { it.id == srcId }?.name } ?: "База",
                    availableTargets = availableTargets,
                    onToggle = { id, enabled ->
                        if (source == null) {
                            // Базовый алиас
                            if (enabled) {
                                clientState.enableAlias(id)
                            } else {
                                clientState.disableAlias(id)
                            }
                        }
                        // TODO: для профильных алиасов пока не поддерживается toggle
                    },
                    onEdit = { aliasToEdit ->
                        editingAlias = aliasToEdit
                        editingAliasSource = source
                        showDialog = true
                    },
                    onDelete = { id ->
                        if (source == null) {
                            clientState.removeAlias(id)
                        } else {
                            clientState.profileManager.removeAliasFromProfile(source, id)
                        }
                    },
                    onMove = { targetProfileId ->
                        // Удаляем из источника
                        if (source == null) {
                            clientState.removeAlias(alias.id)
                        } else {
                            clientState.profileManager.removeAliasFromProfile(source, alias.id)
                        }
                        // Добавляем в цель
                        if (targetProfileId == null) {
                            clientState.addAlias(alias)
                        } else {
                            clientState.profileManager.addAliasToProfile(targetProfileId, alias)
                        }
                    }
                )
            }
        }

        if (aliasesWithSource.value.isEmpty()) {
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
        // Формируем список доступных профилей для добавления
        val availableProfiles = mutableListOf<Pair<String?, String>>(null to "База")
        activeStack.forEach { profileId ->
            profiles.find { it.id == profileId }?.let { profile ->
                availableProfiles.add(profileId to profile.name)
            }
        }

        AliasDialog(
            alias = editingAlias,
            availableProfiles = if (editingAlias == null) availableProfiles else emptyList(),
            initialTargetProfileId = lastTargetProfileId,
            onDismiss = {
                showDialog = false
                editingAlias = null
                editingAliasSource = null
            },
            onSave = { alias, targetProfileId ->
                if (editingAlias == null) {
                    // Новый алиас - добавляем в выбранную цель
                    lastTargetProfileId = targetProfileId  // Запоминаем выбор
                    if (targetProfileId == null) {
                        clientState.addAlias(alias)
                    } else {
                        clientState.profileManager.addAliasToProfile(targetProfileId, alias)
                    }
                } else {
                    // Редактирование - обновляем в исходном месте
                    if (editingAliasSource == null) {
                        clientState.updateAlias(alias)
                    } else {
                        clientState.profileManager.updateAliasInProfile(editingAliasSource!!, alias)
                    }
                }
                showDialog = false
                editingAlias = null
                editingAliasSource = null
            }
        )
    }
}

@Composable
private fun AliasItem(
    alias: Alias,
    source: String?,        // null = база, иначе ID профиля
    sourceName: String,     // Отображаемое имя источника
    availableTargets: List<Pair<String?, String>>,  // (id или null для базы, имя)
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Alias) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (targetProfileId: String?) -> Unit  // null = переместить в базу
) {
    val colorScheme = LocalAppColorScheme.current
    var showMoveMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = if (alias.enabled) colorScheme.surface else colorScheme.surfaceVariant,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Первая строка: бейдж источника, имя, кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Бейдж источника с dropdown для перемещения (цветной)
                Box {
                    Surface(
                        modifier = Modifier.clickable {
                            if (availableTargets.isNotEmpty()) showMoveMenu = true
                        },
                        color = if (source == null) colorScheme.primary.copy(alpha = 0.2f)
                                else colorScheme.secondary.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sourceName,
                                color = if (source == null) colorScheme.primary else colorScheme.secondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (availableTargets.isNotEmpty()) {
                                Text(
                                    text = " ▼",
                                    color = if (source == null) colorScheme.primary else colorScheme.secondary,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showMoveMenu,
                        onDismissRequest = { showMoveMenu = false }
                    ) {
                        availableTargets.forEach { (targetId, targetName) ->
                            DropdownMenuItem(onClick = {
                                onMove(targetId)
                                showMoveMenu = false
                            }) {
                                Text("→ $targetName", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Имя алиаса
                Text(
                    text = alias.name,
                    color = if (alias.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                // Кнопки управления
                Switch(
                    checked = alias.enabled,
                    onCheckedChange = { enabled -> onToggle(alias.id, enabled) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.success,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(width = 40.dp, height = 24.dp)
                )

                Button(
                    onClick = { onEdit(alias) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary),
                    modifier = Modifier.size(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✎", color = Color.White, fontSize = 12.sp)
                }

                Button(
                    onClick = { onDelete(alias.id) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.error),
                    modifier = Modifier.size(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 12.sp)
                }
            }

            // Вторая строка: паттерн (всегда видим)
            Text(
                text = alias.pattern.pattern,
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Третья строка: команды (если есть)
            if (alias.commands.isNotEmpty()) {
                Text(
                    text = "→ ${alias.commands.joinToString("; ")}",
                    color = colorScheme.secondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

