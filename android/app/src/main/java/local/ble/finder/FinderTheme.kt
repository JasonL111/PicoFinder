package local.ble.finder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object FinderStyle {
    val pagePadding = 20.dp
    val cardRadius = 24.dp
    const val signalMotionMs = 450
    const val stateMotionMs = 220
    const val pageMotionMs = 240
}

private val LightFinderColors = lightColorScheme(
    primary = Color(0xff006b58), onPrimary = Color.White,
    primaryContainer = Color(0xffd6f3e8), onPrimaryContainer = Color(0xff123d31),
    secondary = Color(0xff50665e), onSecondary = Color.White,
    secondaryContainer = Color(0xffe1ece6), onSecondaryContainer = Color(0xff20382e),
    tertiary = Color(0xff296b75), onTertiary = Color.White,
    tertiaryContainer = Color(0xffd9f0f2), onTertiaryContainer = Color(0xff163e46),
    background = Color(0xfff4f7f5), onBackground = Color(0xff172c25),
    surface = Color(0xfffcfdfa), onSurface = Color(0xff172c25),
    surfaceVariant = Color(0xffe5ece7), onSurfaceVariant = Color(0xff52655d),
    surfaceContainer = Color(0xffedf2ee), surfaceContainerLow = Color(0xffffffff),
    surfaceContainerHigh = Color(0xffe7eee9), outlineVariant = Color(0xffd3ded7),
)

private val DarkFinderColors = darkColorScheme(
    primary = Color(0xff8cdbbf), onPrimary = Color(0xff00382a),
    primaryContainer = Color(0xff204f3f), onPrimaryContainer = Color(0xffc9f2df),
    secondary = Color(0xffb4ccc0), onSecondary = Color(0xff20372c),
    secondaryContainer = Color(0xff30483c), onSecondaryContainer = Color(0xffd4eade),
    tertiary = Color(0xff9cd2db), onTertiary = Color(0xff00363f),
    tertiaryContainer = Color(0xff214b53), onTertiaryContainer = Color(0xffc5edf2),
    background = Color(0xff101a16), onBackground = Color(0xffe0ece3),
    surface = Color(0xff14201a), onSurface = Color(0xffe0ece3),
    surfaceVariant = Color(0xff2a3a31), onSurfaceVariant = Color(0xffb5c8bb),
    surfaceContainer = Color(0xff1c2b22), surfaceContainerLow = Color(0xff18251e),
    surfaceContainerHigh = Color(0xff25372c), outlineVariant = Color(0xff3b5041),
)

private val FinderTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
internal fun PicoFinderTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkFinderColors else LightFinderColors,
        typography = FinderTypography,
        content = content,
    )
}
