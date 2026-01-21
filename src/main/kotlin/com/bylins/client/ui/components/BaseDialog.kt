package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Базовый диалог с прокручиваемым контентом и sticky кнопками внизу.
 * Кроссплатформенный - работает на Desktop, Android, iOS.
 *
 * @param title Заголовок диалога
 * @param onDismiss Закрытие диалога
 * @param onConfirm Подтверждение (null = без кнопки подтверждения)
 * @param confirmText Текст кнопки подтверждения
 * @param dismissText Текст кнопки отмены
 * @param confirmEnabled Активность кнопки подтверждения
 * @param width Ширина диалога
 * @param maxHeight Максимальная высота контента
 * @param content Содержимое диалога
 */
@Composable
fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    confirmText: String = "Сохранить",
    dismissText: String = "Отмена",
    confirmEnabled: Boolean = true,
    width: Dp = 500.dp,
    maxHeight: Dp = 600.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(width),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Заголовок - всегда видим
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Прокручиваемый контент
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = maxHeight)
                        .verticalScroll(scrollState),
                    content = content
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Разделитель перед кнопками
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Sticky кнопки - всегда видны
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }

                    if (onConfirm != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirm,
                            enabled = confirmEnabled
                        ) {
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Вариант BaseDialog без заголовка (для простых диалогов подтверждения)
 */
@Composable
fun ConfirmDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "Да",
    dismissText: String = "Отмена",
    title: String? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}
