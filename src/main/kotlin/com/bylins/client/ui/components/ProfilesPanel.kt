package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.profiles.Profile
import com.bylins.client.ui.theme.LocalAppColorScheme
import mu.KotlinLogging

private val logger = KotlinLogging.logger("ProfilesPanel")

@Composable
fun ProfilesPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    val profileManager = clientState.profileManager

    val profiles by profileManager.profiles.collectAsState()
    val activeStack by profileManager.activeStack.collectAsState()

    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Обновляем selectedProfile если профиль изменился
    LaunchedEffect(profiles) {
        selectedProfile = selectedProfile?.let { sel ->
            profiles.find { it.id == sel.id }
        }
    }

    // Автоматически скрываем snackbar через 3 секунды
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            kotlinx.coroutines.delay(3000)
            snackbarMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Заголовок
        Text(
            text = "Профили персонажей",
            color = colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Колонка 1: Активный стек
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .background(colorScheme.surface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Активный стек",
                    color = colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "(сверху вниз = порядок)",
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (activeStack.isEmpty()) {
                    Text(
                        text = "(только база)",
                        color = colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(activeStack) { profileId ->
                            val profile = profiles.find { it.id == profileId }
                            if (profile != null) {
                                StackItem(
                                    profile = profile,
                                    colorScheme = colorScheme,
                                    onRemove = {
                                        val removed = profileManager.removeFromStack(profile.id)
                                        if (removed.size > 1) {
                                            val dependentNames = removed.filter { it != profile.id }
                                                .mapNotNull { id -> profiles.find { it.id == id }?.name }
                                            snackbarMessage = "Также отключены: ${dependentNames.joinToString()}"
                                        }
                                        clientState.saveConfig()
                                    },
                                    onClick = { selectedProfile = profile }
                                )
                            }
                        }
                    }
                }

                // Кнопка очистки стека
                if (activeStack.isNotEmpty()) {
                    Button(
                        onClick = {
                            profileManager.clearStack()
                            clientState.saveConfig()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.error)
                    ) {
                        Text("Очистить стек", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Колонка 2: Все профили
            Column(
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight()
                    .background(colorScheme.surface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Доступные профили",
                    color = colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(profiles) { profile ->
                        val isInStack = profile.id in activeStack
                        ProfileListItem(
                            profile = profile,
                            isInStack = isInStack,
                            isSelected = selectedProfile?.id == profile.id,
                            colorScheme = colorScheme,
                            onToggle = {
                                if (isInStack) {
                                    // Удаляем из стека
                                    val removed = profileManager.removeFromStack(profile.id)
                                    if (removed.size > 1) {
                                        val dependentNames = removed.filter { it != profile.id }
                                            .mapNotNull { id -> profiles.find { it.id == id }?.name }
                                        snackbarMessage = "Также отключены: ${dependentNames.joinToString()}"
                                    }
                                    clientState.saveConfig()
                                } else {
                                    // Добавляем в стек (с автоматическими зависимостями)
                                    val result = profileManager.pushProfile(profile.id)
                                    if (!result.success) {
                                        snackbarMessage = result.errorMessage
                                    } else {
                                        // Показываем сообщение о добавленных зависимостях
                                        val addedDeps = result.addedProfiles.filter { it != profile.id }
                                        if (addedDeps.isNotEmpty()) {
                                            val depNames = addedDeps.mapNotNull { id ->
                                                profiles.find { it.id == id }?.name
                                            }
                                            snackbarMessage = "Автоматически добавлены: ${depNames.joinToString()}"
                                        }
                                        clientState.saveConfig()
                                    }
                                }
                            },
                            onClick = { selectedProfile = profile }
                        )
                    }
                }

                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Создать профиль", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Колонка 3: Редактор профиля
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colorScheme.surface)
                    .padding(8.dp)
            ) {
                selectedProfile?.let { profile ->
                    ProfileEditor(
                        profile = profile,
                        allProfiles = profiles,
                        colorScheme = colorScheme,
                        onSave = { updated ->
                            profileManager.saveProfile(updated)
                            selectedProfile = updated
                        },
                        onDelete = {
                            profileManager.deleteProfile(profile.id)
                            selectedProfile = null
                        },
                        onReload = {
                            profileManager.reloadProfile(profile.id)
                        }
                    )
                } ?: run {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Выберите профиль для редактирования",
                            color = colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        }

        // Snackbar внизу (внутри Box)
        snackbarMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                backgroundColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurface,
                action = {
                    IconButton(
                        onClick = { snackbarMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            ) {
                Text(message, fontSize = 12.sp)
            }
        }
    }

    // Диалог создания профиля
    if (showCreateDialog) {
        CreateProfileDialog(
            colorScheme = colorScheme,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                val profile = profileManager.createProfile(name, description)
                selectedProfile = profile
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun StackItem(
    profile: Profile,
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = colorScheme.primary.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    color = colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.requires.isNotEmpty()) {
                    Text(
                        text = "req: ${profile.requires.joinToString()}",
                        color = colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Убрать из стека",
                    tint = colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileListItem(
    profile: Profile,
    isInStack: Boolean,
    isSelected: Boolean,
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> colorScheme.primary.copy(alpha = 0.3f)
        isInStack -> colorScheme.secondary.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)  // Клик выбирает для редактирования
            .then(
                if (isSelected) Modifier.border(1.dp, colorScheme.primary, RoundedCornerShape(4.dp))
                else Modifier
            ),
        backgroundColor = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка переключения (чекбокс)
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isInStack) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (isInStack) "Убрать из стека" else "Добавить в стек",
                    tint = if (isInStack) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    color = colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.description.isNotEmpty()) {
                    Text(
                        text = profile.description,
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Показываем зависимости если есть
                if (profile.requires.isNotEmpty()) {
                    Text(
                        text = "→ ${profile.requires.joinToString()}",
                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: Profile,
    allProfiles: List<Profile>,
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onReload: () -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var description by remember(profile.id) { mutableStateOf(profile.description) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Заголовок с кнопками
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Редактирование",
                color = colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onReload, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Перезагрузить", tint = colorScheme.onSurface)
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Удалить", tint = colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ID профиля (не редактируемый)
        Text(
            text = "ID: ${profile.id}",
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Имя профиля
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя профиля", color = colorScheme.onSurface) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = colorScheme.onBackground,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Описание
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Описание", color = colorScheme.onSurface) },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = colorScheme.onBackground,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Зависимости с возможностью редактирования
        var currentRequires by remember(profile.id) { mutableStateOf(profile.requires) }
        var showDependencyPicker by remember { mutableStateOf(false) }

        Text(
            text = "Зависимости (requires):",
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentRequires.isEmpty()) {
                Text(
                    text = "(нет)",
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                currentRequires.forEach { reqId ->
                    val reqProfile = allProfiles.find { it.id == reqId }
                    ChipWithRemove(
                        text = reqProfile?.name ?: reqId,
                        colorScheme = colorScheme,
                        onRemove = {
                            currentRequires = currentRequires - reqId
                        }
                    )
                }
            }

            // Кнопка добавления зависимости
            Box {
                IconButton(
                    onClick = { showDependencyPicker = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Добавить зависимость",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Выпадающий список профилей
                DropdownMenu(
                    expanded = showDependencyPicker,
                    onDismissRequest = { showDependencyPicker = false }
                ) {
                    // Показываем профили, которые ещё не в зависимостях и не сам профиль
                    val availableProfiles = allProfiles.filter {
                        it.id != profile.id && it.id !in currentRequires
                    }

                    if (availableProfiles.isEmpty()) {
                        DropdownMenuItem(onClick = { showDependencyPicker = false }) {
                            Text("(нет доступных)", color = colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        availableProfiles.forEach { availableProfile ->
                            DropdownMenuItem(onClick = {
                                currentRequires = currentRequires + availableProfile.id
                                showDependencyPicker = false
                            }) {
                                Text(availableProfile.name, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // Кнопка сохранения зависимостей если изменились
        if (currentRequires != profile.requires) {
            Button(
                onClick = {
                    onSave(profile.copy(requires = currentRequires))
                },
                modifier = Modifier.padding(top = 4.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.warning),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Сохранить зависимости", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Статистика содержимого
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = colorScheme.surface,
            elevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Содержимое:",
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Триггеров: ${profile.triggers.size}",
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Алиасов: ${profile.aliases.size}",
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Хоткеев: ${profile.hotkeys.size}",
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Переменных: ${profile.variables.size}",
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                profile.scriptsDir?.let { scriptsDir ->
                    val scriptCount = scriptsDir.toFile().listFiles()?.count {
                        it.isFile && (it.extension in listOf("js", "lua", "py"))
                    } ?: 0
                    Text(
                        text = "Скриптов: $scriptCount",
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка сохранения
        val isModified = name != profile.name || description != profile.description
        Button(
            onClick = {
                onSave(profile.copy(name = name, description = description))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = colorScheme.primary,
                disabledBackgroundColor = colorScheme.surface,
                disabledContentColor = colorScheme.onSurface.copy(alpha = 0.4f)
            ),
            enabled = isModified
        ) {
            Text(
                "Сохранить изменения",
                color = if (isModified) Color.White else colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }

    // Диалог подтверждения удаления
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить профиль?", color = colorScheme.onBackground) },
            text = {
                Text(
                    "Профиль \"${profile.name}\" будет удалён со всеми триггерами, алиасами и скриптами.",
                    color = colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.error)
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.surface)
                ) {
                    Text("Отмена", color = colorScheme.onSurface)
                }
            },
            backgroundColor = colorScheme.background
        )
    }
}

@Composable
private fun Chip(
    text: String,
    colorScheme: com.bylins.client.ui.theme.ColorScheme
) {
    Card(
        backgroundColor = colorScheme.secondary.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = colorScheme.onSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ChipWithRemove(
    text: String,
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onRemove: () -> Unit
) {
    Card(
        backgroundColor = colorScheme.secondary.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Text(
                text = text,
                color = colorScheme.onSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    "Удалить",
                    tint = colorScheme.error,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать профиль", color = colorScheme.onBackground) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя профиля", color = colorScheme.onSurface) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = colorScheme.onBackground,
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (опционально)", color = colorScheme.onSurface) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = colorScheme.onBackground,
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description) },
                colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary),
                enabled = name.isNotBlank()
            ) {
                Text("Создать", color = Color.White)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.surface)
            ) {
                Text("Отмена", color = colorScheme.onSurface)
            }
        },
        backgroundColor = colorScheme.background
    )
}
