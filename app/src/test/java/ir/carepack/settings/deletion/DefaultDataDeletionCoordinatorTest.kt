package ir.carepack.settings.deletion

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.id.IdSource
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.reminder.notification.NotificationGateway
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDataDeletionCoordinatorTest {

    @Test
    fun deleteEverything_runsStateMachineAndFinalVerificationInOrder() =
        runTest {
            val events = mutableListOf<String>()
            val markerStore = RecordingDataDeletionMarkerStore(events)
            val coordinator =
                coordinator(
                    events = events,
                    markerStore = markerStore,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            val result = coordinator.deleteEverything()

            assertEquals(DataDeletionResult.Completed, result)
            assertEquals(
                listOf(
                    "marker-save:PLATFORM_CLEANUP_PENDING",
                    "reminders-cancel-all",
                    "notifications-cancel-all",
                    "auxiliary-clear",
                    "marker-stage:DOMAIN_DATA_PENDING",
                    "domain-clear",
                    "marker-stage:PREFERENCES_PENDING",
                    "preferences-clear-preserving-markers",
                    "marker-stage:TEMPORARY_DATA_PENDING",
                    "temporary-clear",
                    "marker-stage:FINAL_PLATFORM_VERIFICATION_PENDING",
                    "reminders-cancel-all",
                    "notifications-cancel-all",
                    "auxiliary-clear",
                    "marker-stage:COMPLETION_PENDING",
                    "marker-clear",
                ),
                events,
            )
            assertTrue(markerStore.state.first() is DataDeletionMarkerReadResult.Absent)
        }

    @Test
    fun failureKeepsMarkerAtRetryableStageAndResumeContinuesIdempotently() =
        runTest {
            val events = mutableListOf<String>()
            val markerStore = RecordingDataDeletionMarkerStore(events)
            var failTemporary = true
            val coordinator =
                coordinator(
                    events = events,
                    markerStore = markerStore,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    temporaryCleaner =
                        TemporaryDataCleaner {
                            events += "temporary-clear"
                            if (failTemporary) {
                                throw IOException("raw temporary failure")
                            }
                        },
                )

            val first = coordinator.deleteEverything()

            assertTrue(first is DataDeletionResult.Failed)
            assertEquals(
                DataDeletionMarkerStage.TEMPORARY_DATA_PENDING,
                (markerStore.state.first() as DataDeletionMarkerReadResult.Valid)
                    .marker.stage,
            )

            failTemporary = false
            val second = coordinator.resumeIncompleteDeletionIfNeeded()

            assertEquals(DataDeletionResult.Completed, second)
            assertTrue(markerStore.state.first() is DataDeletionMarkerReadResult.Absent)
        }

    @Test
    fun corruptedMarker_failsClosedWithoutCleanupOrReconciliation() =
        runTest {
            val events = mutableListOf<String>()
            val markerStore =
                RecordingDataDeletionMarkerStore(
                    events = events,
                    initial =
                        DataDeletionMarkerReadResult.Corrupted(
                            DeletionMarkerCorruptionReason.CHECKSUM_MISMATCH,
                        ),
                )
            val coordinator =
                coordinator(
                    events = events,
                    markerStore = markerStore,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            val result = coordinator.resumeIncompleteDeletionIfNeeded()

            assertTrue(result is DataDeletionResult.Failed)
            assertTrue(events.isEmpty())
        }

    private fun coordinator(
        events: MutableList<String>,
        markerStore: RecordingDataDeletionMarkerStore,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        temporaryCleaner: TemporaryDataCleaner =
            TemporaryDataCleaner {
                events += "temporary-clear"
            },
    ): DefaultDataDeletionCoordinator =
        DefaultDataDeletionCoordinator(
            markerStore = markerStore,
            reminderCoordinator = RecordingDeletionReminderCoordinator(events),
            notificationGateway = RecordingDeletionNotificationGateway(events),
            domainDataCleaner = DomainDataCleaner { events += "domain-clear" },
            preferenceDataCleaner =
                PreferenceDataCleaner {
                    events += "preferences-clear-preserving-markers"
                },
            temporaryDataCleaner = temporaryCleaner,
            auxiliaryDeletionStateCleaner =
                AuxiliaryDeletionStateCleaner {
                    events += "auxiliary-clear"
                },
            operationGate = AppOperationGate(),
            idSource = IdSource { "delete-all-operation" },
            clock =
                Clock.fixed(
                    Instant.parse("2026-06-24T08:00:00Z"),
                    ZoneOffset.UTC,
                ),
            ioDispatcher = dispatcher,
        )
}

private class RecordingDataDeletionMarkerStore(
    private val events: MutableList<String>,
    initial: DataDeletionMarkerReadResult = DataDeletionMarkerReadResult.Absent,
) : DataDeletionMarkerStore {
    private val mutableState = MutableStateFlow(initial)
    override val state: Flow<DataDeletionMarkerReadResult> = mutableState

    override suspend fun save(marker: DataDeletionMarker) {
        events += "marker-save:${marker.stage.name}"
        mutableState.value = DataDeletionMarkerReadResult.Valid(marker)
    }

    override suspend fun updateStage(
        operationId: String,
        stage: DataDeletionMarkerStage,
    ) {
        events += "marker-stage:${stage.name}"
        val current =
            (mutableState.value as DataDeletionMarkerReadResult.Valid).marker
        require(current.operationId == operationId)
        mutableState.value =
            DataDeletionMarkerReadResult.Valid(current.withStage(stage))
    }

    override suspend fun clear(operationId: String) {
        events += "marker-clear"
        val current =
            (mutableState.value as DataDeletionMarkerReadResult.Valid).marker
        require(current.operationId == operationId)
        mutableState.value = DataDeletionMarkerReadResult.Absent
    }
}

private class RecordingDeletionReminderCoordinator(
    private val events: MutableList<String>,
) : ReminderCoordinator {
    override suspend fun currentStatus(): ReminderStatus = status()

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult =
        ReminderReconciliationResult.Reconciled(
            reason = reason,
            status = status(),
            scheduledCount = 0,
            cancelledCount = 0,
        )

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult = error("Not used")

    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome = RemindLaterOutcome.SchedulingFailed

    override suspend fun cancelAllOwnedReminderState() {
        events += "reminders-cancel-all"
    }

    private fun status() =
        ReminderStatus(
            remindersEnabled = false,
            notificationPermissionGranted = true,
            hasActiveSchedule = false,
            exactAlarmCapabilityGranted = true,
            availability = ReminderAvailability.DISABLED,
        )
}

private class RecordingDeletionNotificationGateway(
    private val events: MutableList<String>,
) : NotificationGateway {
    override fun post(
        notification: ir.carepack.domain.reminder.ReminderNotification,
    ) = Unit

    override fun cancel(occurrenceId: String) = Unit

    override fun cancelAll() {
        events += "notifications-cancel-all"
    }
}
