package tech.unispace.pillreminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF2E7D6F)
private val TealDark = Color(0xFF7FD1C0)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Color(0xFF4F6C66),
    tertiary = Color(0xFF7A5C2E),
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    secondary = Color(0xFFB6CCC6),
    tertiary = Color(0xFFE6C08A),
)

@Composable
fun PillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
