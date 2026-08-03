package ir.carepack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience

@Composable
fun CarePackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seniorMode: SeniorMode = SeniorMode.STANDARD,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }

    val experience =
        CarePackExperience.forMode(
            seniorMode,
        )

    CompositionLocalProvider(
        LocalLayoutDirection provides
                LayoutDirection.Rtl,
        LocalCarePackExperience provides
                experience,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography =
                carePackTypography(
                    seniorMode,
                ),
            content = content,
        )
    }
}

private fun carePackTypography(
    seniorMode: SeniorMode,
): Typography =
    if (seniorMode == SeniorMode.STANDARD) {
        Typography
    } else {
        Typography.copy(
            displayLarge =
                Typography.displayLarge.copy(
                    fontSize = 60.sp,
                    lineHeight = 68.sp,
                ),
            displayMedium =
                Typography.displayMedium.copy(
                    fontSize = 50.sp,
                    lineHeight = 58.sp,
                ),
            displaySmall =
                Typography.displaySmall.copy(
                    fontSize = 42.sp,
                    lineHeight = 50.sp,
                ),
            headlineLarge =
                Typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                ),
            headlineMedium =
                Typography.headlineMedium.copy(
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                ),
            headlineSmall =
                Typography.headlineSmall.copy(
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                ),
            titleLarge =
                Typography.titleLarge.copy(
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                ),
            titleMedium =
                Typography.titleMedium.copy(
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                ),
            titleSmall =
                Typography.titleSmall.copy(
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                ),
            bodyLarge =
                Typography.bodyLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                ),
            bodyMedium =
                Typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                ),
            bodySmall =
                Typography.bodySmall.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                ),
            labelLarge =
                Typography.labelLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                ),
            labelMedium =
                Typography.labelMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                ),
            labelSmall =
                Typography.labelSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
        )
    }
