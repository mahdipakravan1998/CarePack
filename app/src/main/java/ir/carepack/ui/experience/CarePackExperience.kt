package ir.carepack.ui.experience

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.carepack.domain.experience.SeniorMode

@Immutable
data class CarePackExperience(
    val seniorMode: SeniorMode,
    val screenHorizontalPadding: Dp,
    val screenVerticalPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val compactSpacing: Dp,
    val primaryActionMinHeight: Dp,
    val controlMinHeight: Dp,
    val calendarCellMinHeight: Dp,
    val dialogHorizontalPadding: Dp,
) {
    val isSimple: Boolean
        get() = seniorMode == SeniorMode.SIMPLE

    companion object {
        fun forMode(
            seniorMode: SeniorMode,
        ): CarePackExperience =
            when (seniorMode) {
                SeniorMode.STANDARD ->
                    CarePackExperience(
                        seniorMode = seniorMode,
                        screenHorizontalPadding = 24.dp,
                        screenVerticalPadding = 16.dp,
                        sectionSpacing = 20.dp,
                        itemSpacing = 12.dp,
                        compactSpacing = 8.dp,
                        primaryActionMinHeight = 52.dp,
                        controlMinHeight = 48.dp,
                        calendarCellMinHeight = 64.dp,
                        dialogHorizontalPadding = 20.dp,
                    )

                SeniorMode.SIMPLE ->
                    CarePackExperience(
                        seniorMode = seniorMode,
                        screenHorizontalPadding = 20.dp,
                        screenVerticalPadding = 20.dp,
                        sectionSpacing = 28.dp,
                        itemSpacing = 18.dp,
                        compactSpacing = 12.dp,
                        primaryActionMinHeight = 64.dp,
                        controlMinHeight = 56.dp,
                        calendarCellMinHeight = 82.dp,
                        dialogHorizontalPadding = 16.dp,
                    )
            }
    }
}

val LocalCarePackExperience =
    staticCompositionLocalOf {
        CarePackExperience.forMode(
            SeniorMode.STANDARD,
        )
    }

@Composable
fun carePackExperience(): CarePackExperience =
    LocalCarePackExperience.current
