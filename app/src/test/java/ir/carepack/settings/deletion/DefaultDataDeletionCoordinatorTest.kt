package ir.carepack.settings.deletion

import ir.carepack.data.preferences.PrivacyPreferenceState
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.reminder.notification.NotificationGateway
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDataDeletionCoordinatorTest {

    @Test
    fun deleteEverything_runsEveryStageInRequiredOrder() =
        runTest {
            val events =
                mutableListOf<String>()

            val preferenceStore =
                OrderedPrivacyPreferenceStore(
                    events = events,
                )

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        preferenceStore,
                    events = events,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            val result =
                coordinator
                    .deleteEverything()

            assertEquals(
                DataDeletionResult.Completed,
                result,
            )

            assertEquals(
                listOf(
                    "mark",
                    "reminders",
                    "notifications",
                    "domain",
                    "preferences",
                    "temporary",
                    "complete",
                ),
                events,
            )

            assertFalse(
                preferenceStore
                    .currentState
                    .deletionInProgress,
            )

            assertFalse(
                preferenceStore
                    .currentState
                    .includeRecipientName,
            )
        }

    @Test
    fun failedStage_keepsRecoveryMarkerAndStopsLaterStages() =
        runTest {
            val events =
                mutableListOf<String>()

            val preferenceStore =
                OrderedPrivacyPreferenceStore(
                    events = events,
                )

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        preferenceStore,
                    events = events,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                    temporaryFailure =
                        IOException(
                            "Temporary cleanup failed.",
                        ),
                )

            val result =
                coordinator
                    .deleteEverything()

            assertEquals(
                DataDeletionResult.Failed(
                    stage =
                        DataDeletionStage
                            .CLEARING_TEMPORARY_DATA,
                ),
                result,
            )

            assertEquals(
                listOf(
                    "mark",
                    "reminders",
                    "notifications",
                    "domain",
                    "preferences",
                    "temporary",
                ),
                events,
            )

            assertTrue(
                preferenceStore
                    .currentState
                    .deletionInProgress,
            )
        }

    @Test
    fun resumeWithoutMarker_returnsNoDeletionPending() =
        runTest {
            val events =
                mutableListOf<String>()

            val preferenceStore =
                OrderedPrivacyPreferenceStore(
                    events = events,
                    initialState =
                        PrivacyPreferenceState(
                            includeRecipientName =
                                true,
                            deletionInProgress =
                                false,
                        ),
                )

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        preferenceStore,
                    events = events,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            val result =
                coordinator
                    .resumeIncompleteDeletionIfNeeded()

            assertEquals(
                DataDeletionResult
                    .NoDeletionPending,
                result,
            )

            assertTrue(
                events.isEmpty(),
            )
        }

    @Test
    fun resumeWithMarker_repeatsCleanupWithoutMarkingAgain() =
        runTest {
            val events =
                mutableListOf<String>()

            val preferenceStore =
                OrderedPrivacyPreferenceStore(
                    events = events,
                    initialState =
                        PrivacyPreferenceState(
                            includeRecipientName =
                                true,
                            deletionInProgress =
                                true,
                        ),
                )

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        preferenceStore,
                    events = events,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            val result =
                coordinator
                    .resumeIncompleteDeletionIfNeeded()

            assertEquals(
                DataDeletionResult.Completed,
                result,
            )

            assertEquals(
                listOf(
                    "reminders",
                    "notifications",
                    "domain",
                    "preferences",
                    "temporary",
                    "complete",
                ),
                events,
            )

            assertFalse(
                preferenceStore
                    .currentState
                    .deletionInProgress,
            )
        }

    @Test
    fun repeatedDeletion_isIdempotentAtCoordinatorBoundary() =
        runTest {
            val events =
                mutableListOf<String>()

            val preferenceStore =
                OrderedPrivacyPreferenceStore(
                    events = events,
                )

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        preferenceStore,
                    events = events,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                DataDeletionResult.Completed,
                coordinator
                    .deleteEverything(),
            )

            assertEquals(
                DataDeletionResult.Completed,
                coordinator
                    .deleteEverything(),
            )

            assertEquals(
                2,
                events.count {
                    it == "complete"
                },
            )

            assertFalse(
                preferenceStore
                    .currentState
                    .deletionInProgress,
            )
        }

    private fun createCoordinator(
        preferenceStore:
        PrivacyPreferenceStore,
        events:
        MutableList<String>,
        dispatcher:
        CoroutineDispatcher,
        temporaryFailure:
        Throwable? = null,
    ): DefaultDataDeletionCoordinator =
        DefaultDataDeletionCoordinator(
            privacyPreferenceStore =
                preferenceStore,
            reminderCoordinator =
                OrderedReminderCoordinator(
                    events = events,
                ),
            notificationGateway =
                OrderedNotificationGateway(
                    events = events,
                ),
            domainDataCleaner =
                DomainDataCleaner {
                    events +=
                        "domain"
                },
            temporaryDataCleaner =
                TemporaryDataCleaner {
                    events +=
                        "temporary"

                    temporaryFailure
                        ?.let {
                            throw it
                        }
                },
            ioDispatcher =
                dispatcher,
        )
}

private class OrderedPrivacyPreferenceStore(
    private val events:
    MutableList<String>,
    initialState:
    PrivacyPreferenceState =
        PrivacyPreferenceState(),
) : PrivacyPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<PrivacyPreferenceState> =
        mutableState

    val currentState:
            PrivacyPreferenceState
        get() =
            mutableState.value

    override suspend fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    includeRecipientName =
                        includeRecipientName,
                )
    }

    override suspend fun markDeletionInProgress() {
        events +=
            "mark"

        mutableState.value =
            mutableState
                .value
                .copy(
                    deletionInProgress =
                        true,
                )
    }

    override suspend fun clearAllPreservingDeletionMarker() {
        events +=
            "preferences"

        mutableState.value =
            PrivacyPreferenceState(
                includeRecipientName =
                    false,
                deletionInProgress =
                    true,
            )
    }

    override suspend fun completeDeletion() {
        events +=
            "complete"

        mutableState.value =
            PrivacyPreferenceState()
    }
}

private class OrderedReminderCoordinator(
    private val events:
    MutableList<String>,
) : ReminderCoordinator {

    override suspend fun currentStatus():
            ReminderStatus =
        ReminderStatus(
            remindersEnabled =
                false,
            notificationPermissionGranted =
                true,
            hasActiveSchedule =
                false,
            exactAlarmCapabilityGranted =
                false,
            availability =
                ReminderAvailability.DISABLED,
        )

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult =
        ReminderReconciliationResult
            .Reconciled(
                reason = reason,
                status =
                    currentStatus(),
                scheduledCount =
                    0,
                cancelledCount =
                    0,
            )

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        error(
            "Alarm firing is not used by this test.",
        )
    }

    override suspend fun cancelAllOwnedReminderState() {
        events +=
            "reminders"
    }
}

private class OrderedNotificationGateway(
    private val events:
    MutableList<String>,
) : NotificationGateway {

    override fun post(
        notification:
        ReminderNotification,
    ) {
        Unit
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        Unit
    }

    override fun cancelAll() {
        events +=
            "notifications"
    }
}
