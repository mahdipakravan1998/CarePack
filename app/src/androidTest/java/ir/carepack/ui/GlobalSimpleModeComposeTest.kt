package ir.carepack.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.feature.settings.SettingsRoute
import ir.carepack.feature.settings.SettingsViewModel
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.InstrumentedUserExperiencePreferenceStore
import ir.carepack.ui.theme.CarePackTheme
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalSimpleModeComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun persistedModeCanSwitchBackToStandardAndReturnToSimple() {
        val store =
            InstrumentedUserExperiencePreferenceStore(
                initialState =
                    UserExperiencePreferenceState(
                        seniorMode =
                            SeniorMode.SIMPLE,
                    ),
            )

        val viewModel =
            SettingsViewModel(
                userExperiencePreferenceStore = store,
                zoneProvider =
                    ZoneProvider {
                        ZoneId.of("UTC")
                    },
                appVersion = "1.0.0",
            )

        composeRule.setContent {
            CarePackTheme(
                seniorMode = SeniorMode.SIMPLE,
            ) {
                SettingsRoute(
                    viewModel = viewModel,
                    onOpenReminderSettings = {},
                    onOpenPrivacy = {},
                    onDeleteAllData = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "display_simple",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()

        composeRule
            .onNodeWithTag(
                "display_standard",
            )
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(
            timeoutMillis = WAIT_TIMEOUT_MILLIS,
        ) {
            runBlocking {
                store
                    .state
                    .first()
                    .seniorMode ==
                        SeniorMode.STANDARD
            }
        }

        composeRule
            .onNodeWithTag(
                "display_standard",
            )
            .assertIsSelected()

        composeRule
            .onNodeWithTag(
                "display_simple",
            )
            .performClick()

        composeRule.waitUntil(
            timeoutMillis = WAIT_TIMEOUT_MILLIS,
        ) {
            runBlocking {
                store
                    .state
                    .first()
                    .seniorMode ==
                        SeniorMode.SIMPLE
            }
        }

        composeRule
            .onNodeWithTag(
                "display_simple",
            )
            .assertIsSelected()
    }

    @Test
    fun changingPresentationModeDoesNotMutateCareData() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی ثابت",
                        instruction = "صبح",
                        minutesOfDay =
                            listOf(8 * 60),
                    )

                val medicationCountBefore =
                    fixture
                        .database
                        .medicationDao()
                        .count()

                val occurrenceCountBefore =
                    fixture
                        .database
                        .occurrenceDao()
                        .count()

                val reportCountBefore =
                    fixture
                        .database
                        .reportingDao()
                        .countReports()

                val store =
                    InstrumentedUserExperiencePreferenceStore()

                store.setSeniorMode(
                    SeniorMode.SIMPLE,
                )

                store.setSeniorMode(
                    SeniorMode.STANDARD,
                )

                assertEquals(
                    medicationCountBefore,
                    fixture
                        .database
                        .medicationDao()
                        .count(),
                )

                assertEquals(
                    occurrenceCountBefore,
                    fixture
                        .database
                        .occurrenceDao()
                        .count(),
                )

                assertEquals(
                    reportCountBefore,
                    fixture
                        .database
                        .reportingDao()
                        .countReports(),
                )

                assertEquals(
                    plan.medicationId,
                    fixture
                        .database
                        .medicationDao()
                        .getById(
                            plan.medicationId,
                        )
                        ?.id,
                )
            }
        }

    @Test
    fun simpleModeAtLargeFontKeepsSettingsActionsReachable() {
        val state =
            ir.carepack.feature.settings.SettingsUiState(
                preferenceState =
                    UserExperiencePreferenceState(
                        seniorMode =
                            SeniorMode.SIMPLE,
                    ),
                appVersion = "1.0.0",
            )

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
                    ir.carepack.feature.settings.SettingsScreen(
                        state = state,
                        onOpenReminderSettings = {},
                        onOpenPrivacy = {},
                        onDeleteAllData = {},
                        onFirstDayOfWeekPreferenceChanged = {},
                        onSeniorModeChanged = {},
                    )
                }
            }
        }

        listOf(
            "settings_reminders",
            "settings_privacy",
            "display_simple",
            "settings_delete_all",
        ).forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS =
            5_000L
    }
}
