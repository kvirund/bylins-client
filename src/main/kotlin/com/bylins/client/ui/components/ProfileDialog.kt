package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bylins.client.connection.ConnectionProfile

/**
 * Диалог создания/редактирования профиля подключения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    profile: ConnectionProfile? = null,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var host by remember { mutableStateOf(profile?.host ?: "") }
    var port by remember { mutableStateOf(profile?.port?.toString() ?: "4000") }
    var encoding by remember { mutableStateOf(profile?.encoding ?: "UTF-8") }
    var encodingMenuExpanded by remember { mutableStateOf(false) }

    val availableEncodings = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (profile == null) "Новый профиль" else "Редактировать профиль",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Dropdown для выбора кодировки
                ExposedDropdownMenuBox(
                    expanded = encodingMenuExpanded,
                    onExpandedChange = { encodingMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = encoding,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Кодировка") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = encodingMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = encodingMenuExpanded,
                        onDismissRequest = { encodingMenuExpanded = false }
                    ) {
                        availableEncodings.forEach { enc ->
                            DropdownMenuItem(
                                text = { Text(enc) },
                                onClick = {
                                    encoding = enc
                                    encodingMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val portInt = port.toIntOrNull() ?: 4000
                            val newProfile = if (profile != null) {
                                profile.copy(
                                    name = name,
                                    host = host,
                                    port = portInt,
                                    encoding = encoding
                                )
                            } else {
                                ConnectionProfile(
                                    name = name,
                                    host = host,
                                    port = portInt,
                                    encoding = encoding
                                )
                            }
                            onSave(newProfile)
                        },
                        enabled = name.isNotBlank() && host.isNotBlank() && port.toIntOrNull() != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
