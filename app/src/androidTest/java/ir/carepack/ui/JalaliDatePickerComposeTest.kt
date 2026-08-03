package ir.carepack.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.feature.calendar.JalaliDatePickerDialog
import ir.carepack.ui.theme.CarePackTheme
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JalaliDatePickerComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun selectingDateAndConfirmingReturnsLocalDate() {
        var selectedDate: LocalDate? = null

        composeRule.setContent {
            CarePackTheme {
                JalaliDatePickerDialog(
                    title = "تاریخ شروع",
                    selectedDate = TODAY,
                    today = TODAY,
                    firstDayOfWeek =
                        DayOfWeek.SATURDAY,
                    allowClear = false,
                    onDismissRequest = {},
                    onDateSelected = {
                        selectedDate = it
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_day_${TOMORROW.toEpochDay()}",
            )
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_confirm",
            )
            .performClick()

        assertEquals(
            TOMORROW,
            selectedDate,
        )
    }

    @Test
    fun todayActionSynchronizesSelectionBeforeConfirmation() {
        var selectedDate: LocalDate? = null

        composeRule.setContent {
            CarePackTheme {
                JalaliDatePickerDialog(
                    title = "تاریخ پایان",
                    selectedDate =
                        TODAY.minusDays(10),
                    today = TODAY,
                    firstDayOfWeek =
                        DayOfWeek.MONDAY,
                    allowClear = true,
                    clearAsNoEndDate = true,
                    onDismissRequest = {},
                    onDateSelected = {
                        selectedDate = it
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_today",
            )
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_confirm",
            )
            .performClick()

        assertEquals(
            TODAY,
            selectedDate,
        )
    }

    @Test
    fun clearActionSupportsNoEndDate() {
        var callbackInvoked = false
        var selectedDate: LocalDate? = TODAY

        composeRule.setContent {
            CarePackTheme {
                JalaliDatePickerDialog(
                    title = "تاریخ پایان",
                    selectedDate = TODAY,
                    today = TODAY,
                    firstDayOfWeek =
                        DayOfWeek.SATURDAY,
                    allowClear = true,
                    clearAsNoEndDate = true,
                    onDismissRequest = {},
                    onDateSelected = {
                        callbackInvoked = true
                        selectedDate = it
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_clear",
            )
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_confirm",
            )
            .performClick()

        assertEquals(true, callbackInvoked)
        assertNull(selectedDate)
    }

    @Test
    fun simpleModeAtLargeFontKeepsPickerActionsVisible() {
        composeRule.setContent {
            CarePackTheme(
                seniorMode = SeniorMode.SIMPLE,
            ) {
                val density =
                    LocalDensity.current

                CompositionLocalProvider(
                    LocalDensity provides
                            Density(
                                density = density.density,
                                fontScale = 2f,
                            ),
                ) {
                    JalaliDatePickerDialog(
                        title = "انتخاب تاریخ",
                        selectedDate = TODAY,
                        today = TODAY,
                        firstDayOfWeek =
                            DayOfWeek.SATURDAY,
                        allowClear = true,
                        onDismissRequest = {},
                        onDateSelected = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_today",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_clear",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "jalali_date_picker_confirm",
            )
            .assertIsDisplayed()
    }

    private companion object {
        val TODAY: LocalDate =
            LocalDate.parse(
                "2026-03-21",
            )

        val TOMORROW: LocalDate =
            TODAY.plusDays(1)
    }
}
