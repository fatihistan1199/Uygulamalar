package tr.com.gundemradari.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors=lightColorScheme(
    primary=Color(0xFF245B8A),
    onPrimary=Color.White,
    primaryContainer=Color(0xFFD7E8F8),
    onPrimaryContainer=Color(0xFF102B43),
    secondary=Color(0xFF2E6E69),
    onSecondary=Color.White,
    secondaryContainer=Color(0xFFD8EFEC),
    onSecondaryContainer=Color(0xFF153936),
    tertiary=Color(0xFF8A6427),
    onTertiary=Color.White,
    tertiaryContainer=Color(0xFFF4E4BF),
    onTertiaryContainer=Color(0xFF3E2A08),
    errorContainer=Color(0xFFF7D9D6),
    surface=Color(0xFFF8FAFC),
    surfaceVariant=Color(0xFFE9EEF3)
)

private val DarkColors=darkColorScheme(
    primary=Color(0xFFA8C8E8),
    primaryContainer=Color(0xFF294A67),
    secondary=Color(0xFFA7D4CF),
    secondaryContainer=Color(0xFF274B48),
    tertiary=Color(0xFFE0C58E),
    tertiaryContainer=Color(0xFF59431B),
    errorContainer=Color(0xFF6A3431)
)

@Composable
fun GundemTheme(content:@Composable ()->Unit){
    MaterialTheme(
        colorScheme=if(isSystemInDarkTheme())DarkColors else LightColors,
        content=content
    )
}
