package tech.unispace.pillreminder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * Единые цвета полей ввода для всего приложения.
 * Подсказка (placeholder) — бледная, чтобы не путалась с введённым текстом.
 * Каждый OutlinedTextField в проекте обязан передавать `colors = fieldColors()`.
 */
@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
)
