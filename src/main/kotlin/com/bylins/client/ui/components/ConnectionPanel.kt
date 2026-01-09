package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionPanel(modifier: Modifier = Modifier) {
    var host by remember { mutableStateOf("mud.bylins.su") }
    var port by remember { mutableStateOf("4000") }
    var isConnected by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
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

        Button(
            onClick = {
                isConnected = !isConnected
                // TODO: Implement connection logic
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
}
