package com.parlor.feature.library

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomColors
import com.parlor.core.model.RoomTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDirection

@OptIn(ExperimentalTextApi::class)
@Composable
fun ParlorTheme(theme: RoomTheme = RoomTheme.LINEN, dark: Boolean = false, content: @Composable () -> Unit) {
    val colors = remember(theme, dark) { roomColors(theme, dark) }
    val family = FontFamily(
        Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.nunito, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val defaults = Typography()
    val base = defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        displayMedium = defaults.displayMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        displaySmall = defaults.displaySmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        titleLarge = defaults.titleLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        titleMedium = defaults.titleMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        titleSmall = defaults.titleSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        bodySmall = defaults.bodySmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        labelLarge = defaults.labelLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        labelMedium = defaults.labelMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        labelSmall = defaults.labelSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
    )
    CompositionLocalProvider(LocalBoardSkin provides colors.board) {
    MaterialTheme(colorScheme = colors.scheme, typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = base.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = base.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    ), content = content)
    }
}
