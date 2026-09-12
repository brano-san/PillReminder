package tech.unispace.pillreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Своя палитра вместо динамических цветов системы: иначе приложение окрашивается в цвет
 * обоев (у пользователя — синий), а тёмная тема получается чёрной с серой навигацией.
 * Тёмный фон — глубокий зелёно-серый, а не #000000; навигация сливается с фоном.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D6F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE8DE),
    onPrimaryContainer = Color(0xFF07332B),
    secondary = Color(0xFF4F6C66),
    secondaryContainer = Color(0xFFD5E8E3),
    // Без явного значения M3 подставляет лавандовый дефолт на тональные кнопки («Сон», «Еда», галочка).
    onSecondaryContainer = Color(0xFF0F2A25),
    tertiary = Color(0xFF7A5C2E),
    tertiaryContainer = Color(0xFFFFE3B8),
    onTertiaryContainer = Color(0xFF3F2A05),
    background = Color(0xFFF4F8F6),
    onBackground = Color(0xFF1A201E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A201E),
    surfaceVariant = Color(0xFFE1ECE8),
    onSurfaceVariant = Color(0xFF465753),
    surfaceContainer = Color(0xFFEEF4F2),
    surfaceContainerLow = Color(0xFFF4F8F6),
    // Фон filled-Card в M3 1.3: без него карточки трекеров и секций мастера серо-сиреневые.
    surfaceContainerHighest = Color(0xFFDDE7E3),
    outline = Color(0xFF7A8B86),
    outlineVariant = Color(0xFFC6D3CF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1C0),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1F5C51),
    onPrimaryContainer = Color(0xFFBFE8DE),
    secondary = Color(0xFFB6CCC6),
    secondaryContainer = Color(0xFF35504A),
    onSecondaryContainer = Color(0xFFD5E8E3),
    tertiary = Color(0xFFE6C08A),
    tertiaryContainer = Color(0xFF5A4318),
    onTertiaryContainer = Color(0xFFFFE3B8),
    background = Color(0xFF0F1614),
    onBackground = Color(0xFFDFE7E4),
    surface = Color(0xFF141C1A),
    onSurface = Color(0xFFDFE7E4),
    surfaceVariant = Color(0xFF243230),
    onSurfaceVariant = Color(0xFFB4C4BF),
    surfaceContainer = Color(0xFF1A2422),
    surfaceContainerLow = Color(0xFF141C1A),
    surfaceContainerHighest = Color(0xFF2B3835),
    outline = Color(0xFF7E8F8A),
    outlineVariant = Color(0xFF334541),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF6E2A26),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun PillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
