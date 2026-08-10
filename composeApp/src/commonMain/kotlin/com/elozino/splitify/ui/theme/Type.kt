package com.elozino.splitify.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import com.elozino.splitify.resources.*

@Composable
fun bodyFontFamily() = FontFamily(
    Font(Res.font.Urbanist_Light, weight = FontWeight.Light),
    Font(Res.font.Urbanist_Regular, weight = FontWeight.Normal),
    Font(Res.font.Urbanist_Medium, weight = FontWeight.Medium),
    Font(Res.font.Urbanist_SemiBold, weight = FontWeight.SemiBold),
    Font(Res.font.Urbanist_Bold, weight = FontWeight.Bold),
    Font(Res.font.Urbanist_Black, weight = FontWeight.Black)
)

@Composable
fun AppTypography(): Typography {
    val body = bodyFontFamily()
    val baseline = Typography()
    
    return Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = body),
        displayMedium = baseline.displayMedium.copy(fontFamily = body),
        displaySmall = baseline.displaySmall.copy(fontFamily = body),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = body),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = body),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = body),
        titleLarge = baseline.titleLarge.copy(fontFamily = body),
        titleMedium = baseline.titleMedium.copy(fontFamily = body),
        titleSmall = baseline.titleSmall.copy(fontFamily = body),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = body),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = body),
        bodySmall = baseline.bodySmall.copy(fontFamily = body),
        labelLarge = baseline.labelLarge.copy(fontFamily = body),
        labelMedium = baseline.labelMedium.copy(fontFamily = body),
        labelSmall = baseline.labelSmall.copy(fontFamily = body),
    )
}
