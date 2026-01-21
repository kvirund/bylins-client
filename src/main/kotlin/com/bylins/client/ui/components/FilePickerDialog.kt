package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

/**
 * Режим выбора файла
 */
enum class FilePickerMode {
    OPEN,   // Выбор существующего файла
    SAVE    // Выбор места для сохранения
}

/**
 * Кроссплатформенный диалог выбора файла.
 * Работает на Desktop, Android, iOS без использования JFileChooser.
 *
 * @param mode Режим: OPEN или SAVE
 * @param title Заголовок диалога
 * @param initialDirectory Начальная директория
 * @param extensions Допустимые расширения (без точки), например listOf("json", "txt")
 * @param defaultFileName Имя файла по умолчанию (для режима SAVE)
 * @param onDismiss Закрытие диалога
 * @param onFileSelected Выбран файл (возвращает File)
 */
@Composable
fun FilePickerDialog(
    mode: FilePickerMode,
    title: String,
    initialDirectory: File = File(System.getProperty("user.home")),
    extensions: List<String> = emptyList(),
    defaultFileName: String = "",
    onDismiss: () -> Unit,
    onFileSelected: (File) -> Unit
) {
    var currentDirectory by remember { mutableStateOf(initialDirectory.takeIf { it.exists() && it.isDirectory } ?: File(System.getProperty("user.home"))) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf(defaultFileName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Получаем список файлов/папок
    val items = remember(currentDirectory) {
        try {
            currentDirectory.listFiles()
                ?.filter { file ->
                    if (file.isDirectory) true
                    else if (extensions.isEmpty()) true
                    else extensions.any { ext -> file.name.endsWith(".$ext", ignoreCase = true) }
                }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(550.dp)
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Заголовок
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Текущий путь с навигацией
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка "Вверх"
                    IconButton(
                        onClick = {
                            currentDirectory.parentFile?.let { parent ->
                                if (parent.exists()) {
                                    currentDirectory = parent
                                    selectedFile = null
                                }
                            }
                        },
                        enabled = currentDirectory.parentFile?.exists() == true
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Вверх",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = currentDirectory.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Список файлов
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Папка пуста",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items) { file ->
                                FileItem(
                                    file = file,
                                    isSelected = selectedFile == file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            currentDirectory = file
                                            selectedFile = null
                                        } else {
                                            selectedFile = file
                                            fileName = file.name
                                        }
                                    },
                                    onDoubleClick = {
                                        if (file.isDirectory) {
                                            currentDirectory = file
                                            selectedFile = null
                                        } else {
                                            onFileSelected(file)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Поле ввода имени файла (для режима SAVE)
                if (mode == FilePickerMode.SAVE) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = fileName,
                        onValueChange = {
                            fileName = it
                            errorMessage = null
                        },
                        label = { Text("Имя файла") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it) } }
                    )
                }

                // Расширения
                if (extensions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Типы файлов: ${extensions.joinToString(", ") { "*.$it" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Разделитель
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(12.dp))

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when (mode) {
                                FilePickerMode.OPEN -> {
                                    selectedFile?.let { onFileSelected(it) }
                                }
                                FilePickerMode.SAVE -> {
                                    if (fileName.isBlank()) {
                                        errorMessage = "Введите имя файла"
                                    } else {
                                        var name = fileName
                                        // Добавляем расширение если не указано
                                        if (extensions.isNotEmpty() && !extensions.any { name.endsWith(".$it", ignoreCase = true) }) {
                                            name = "$name.${extensions.first()}"
                                        }
                                        val file = File(currentDirectory, name)
                                        onFileSelected(file)
                                    }
                                }
                            }
                        },
                        enabled = when (mode) {
                            FilePickerMode.OPEN -> selectedFile != null
                            FilePickerMode.SAVE -> fileName.isNotBlank()
                        }
                    ) {
                        Text(if (mode == FilePickerMode.OPEN) "Открыть" else "Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 300) {
                    onDoubleClick()
                } else {
                    onClick()
                }
                lastClickTime = now
            }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (file.isDirectory)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Утилита для открытия файла в системном приложении.
 * Desktop-only функция. На других платформах ничего не делает.
 */
fun openFileInSystem(file: File): Boolean {
    return try {
        // Проверяем что мы на Desktop
        val desktop = java.awt.Desktop.getDesktop()
        if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
            desktop.open(file)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Утилита для открытия директории в файловом менеджере.
 * Desktop-only функция.
 */
fun openDirectoryInSystem(directory: File): Boolean {
    return try {
        val desktop = java.awt.Desktop.getDesktop()
        if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
            desktop.open(directory)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Проверяет доступность Desktop функций
 */
fun isDesktopSupported(): Boolean {
    return try {
        java.awt.Desktop.isDesktopSupported()
    } catch (e: Exception) {
        false
    }
}
