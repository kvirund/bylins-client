package com.bylins.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState

@Composable
fun ConnectionPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    var host by remember { mutableStateOf("bylins.su") }
    var port by remember { mutableStateOf("4000") }
    var encoding by remember { mutableStateOf(clientState.encoding) }
    var encodingMenuExpanded by remember { mutableStateOf(false) }
    val isConnected by clientState.isConnected.collectAsState()
    val errorMessage by clientState.errorMessage.collectAsState()

    val availableEncodings = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                enabled = !isConnected,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                enabled = !isConnected,
                modifier = Modifier.width(100.dp)
            )

            // Dropdown для выбора кодировки
            Box(modifier = Modifier.width(150.dp)) {
                OutlinedTextField(
                    value = encoding,
                    onValueChange = { },
                    label = { Text("Кодировка") },
                    enabled = !isConnected,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = encodingMenuExpanded && !isConnected,
                    onDismissRequest = { encodingMenuExpanded = false }
                ) {
                    availableEncodings.forEach { enc ->
                        DropdownMenuItem(
                            text = { Text(enc) },
                            onClick = {
                                encoding = enc
                                clientState.setEncoding(enc)
                                encodingMenuExpanded = false
                            }
                        )
                    }
                }
                // Кликабельная область для открытия меню
                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { encodingMenuExpanded = true }
                    )
                }
            }

            Button(
                onClick = {
                    if (isConnected) {
                        clientState.disconnect()
                    } else {
                        val portInt = port.toIntOrNull() ?: 4000
                        clientState.connect(host, portInt)
                    }
                },
                colors = if (isConnected) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = if (isConnected) "Disconnect" else "Connect"
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isConnected) "Отключиться" else "Подключиться")
            }
        }

        // Показываем ошибку если есть
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
