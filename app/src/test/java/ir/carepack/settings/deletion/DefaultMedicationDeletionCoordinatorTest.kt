package ir.carepack.settings.deletion

import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.AppFailureKind
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.domain.reminder.SnoozedReminder
import ir.carepack.testing.FakeMedicationDeletionDataSource
import ir.carepack.testing.InMemoryMedicationDeletionMarkerStore
import ir.carepack.testing.InMemorySnoozedReminderStore
import ir.carepack.testing.RecordingAlarmGateway
import ir.carepack.testing.RecordingCoreReminderCoordinator
import ir.carepack.testing.RecordingNotificationGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMedicationDeletionCoordinatorTest {

    @Test
    fun loadPreview_returnsAvailableAndTrimsMedicationId() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = graph(),
                )

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionPreviewResult
                    .Available(PREVIEW),
                coordinator.loadPreview(
                    "  medication-1  ",
                ),
            )
            assertEquals(
                listOf("medication-1"),
                dataSource.previewRequests,
            )
        }

    @Test
    fun loadPreview_blankIdIsNotFoundWithoutDataAccess() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = graph(),
                )

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionPreviewResult.NotFound,
                coordinator.loadPreview("   "),
            )
            assertTrue(
                dataSource.previewRequests.isEmpty(),
            )
        }

    @Test
    fun deleteMedication_runsTargetCleanupDeletesGraphReconcilesAndClearsMarker() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = graph(),
                )

            val markerStore =
                InMemoryMedicationDeletionMarkerStore()

            val alarmGateway =
                RecordingAlarmGateway()

            val notificationGateway =
                RecordingNotificationGateway()

            val snoozeStore =
                InMemorySnoozedReminderStore(
                    initialReminders =
                        listOf(
                            snooze("occurrence-1"),
                            snooze("other-occurrence"),
                        ),
                )

            val reminderCoordinator =
                RecordingCoreReminderCoordinator()

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    markerStore = markerStore,
                    alarmGateway = alarmGateway,
                    notificationGateway =
                        notificationGateway,
                    snoozeStore = snoozeStore,
                    reminderCoordinator =
                        reminderCoordinator,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            val result =
                coordinator.deleteMedication(PREVIEW)

            assertEquals(
                MedicationDeletionResult.Completed(
                    counts = COUNTS,
                ),
                result,
            )

            assertEquals(
                setOf(
                    AlarmKey.forScheduleSeries(
                        "series-1",
                    ),
                    AlarmKey.forScheduleSeries(
                        "series-2",
                    ),
                    AlarmKey.forDelayedOccurrence(
                        "occurrence-1",
                    ),
                    AlarmKey.forDelayedOccurrence(
                        "occurrence-2",
                    ),
                ),
                alarmGateway.cancelledKeys.toSet(),
            )

            assertEquals(
                listOf("occurrence-1"),
                snoozeStore.deletedOccurrenceIds,
            )

            assertEquals(
                setOf(
                    "occurrence-1",
                    "occurrence-2",
                ),
                notificationGateway
                    .cancelledOccurrenceIds
                    .toSet(),
            )

            assertEquals(1, dataSource.deletionRequests.size)
            assertEquals(
                listOf(
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
                ),
                reminderCoordinator.reconcileReasons,
            )
            assertEquals(
                listOf(
                    "medication-1" to
                        MedicationDeletionMarkerStage
                            .DATABASE_DELETE_PENDING,
                    "medication-1" to
                        MedicationDeletionMarkerStage
                            .DATABASE_DELETED,
                    "medication-1" to
                        MedicationDeletionMarkerStage
                            .FINAL_RECONCILIATION_PENDING,
                ),
                markerStore.stageUpdates,
            )
            assertEquals(
                listOf("medication-1"),
                markerStore.clearedMedicationIds,
            )
            assertNull(markerStore.marker.first())
            assertEquals(0, notificationGateway.cancelAllCallCount)
            assertEquals(0, reminderCoordinator.cancelAllOwnedCallCount)
        }

    @Test
    fun changedGraphBeforeMarker_requiresFreshPreviewAndDoesNotCleanPlatform() =
        runTest {
            val changedPreview =
                PREVIEW.copy(
                    medicationUpdatedAtEpochMillis = 2L,
                    occurrenceCount = 3,
                )

            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph =
                        graph(
                            preview = changedPreview,
                        ),
                )

            val markerStore =
                InMemoryMedicationDeletionMarkerStore()

            val alarmGateway =
                RecordingAlarmGateway()

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    markerStore = markerStore,
                    alarmGateway = alarmGateway,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionResult
                    .ChangedSincePreview(
                        latestPreview =
                            changedPreview,
                    ),
                coordinator.deleteMedication(PREVIEW),
            )

            assertTrue(markerStore.savedMarkers.isEmpty())
            assertTrue(alarmGateway.cancelledKeys.isEmpty())
            assertTrue(dataSource.deletionRequests.isEmpty())
        }

    @Test
    fun platformCleanupFailureKeepsMarkerAndDoesNotDeleteDatabaseGraph() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = graph(),
                )

            val markerStore =
                InMemoryMedicationDeletionMarkerStore()

            val alarmGateway =
                RecordingAlarmGateway().apply {
                    cancelFailure =
                        IllegalStateException(
                            "Alarm cancellation failed.",
                        )
                }

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    markerStore = markerStore,
                    alarmGateway = alarmGateway,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionResult.Failed(
                    stage =
                        MedicationDeletionStage
                            .CANCELLING_SCHEDULE_ALARMS,
                    databaseDeleted = false,
                    failure =
                        SafeAppFailure(
                            kind = AppFailureKind.CORRUPTION,
                            stage =
                                AppOperationStage
                                    .CANCELLING_ALARMS,
                            retryable = false,
                        ),
                ),
                coordinator.deleteMedication(PREVIEW),
            )

            assertTrue(
                dataSource.deletionRequests.isEmpty(),
            )
            assertEquals(
                MedicationDeletionMarkerStage
                    .PLATFORM_CLEANUP_PENDING,
                markerStore.marker.first()?.stage,
            )
        }

    @Test
    fun databaseFailureKeepsGraphAndRecoveryMarker() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = graph(),
                ).apply {
                    deleteFailure =
                        IllegalStateException(
                            "Database deletion failed.",
                        )
                }

            val markerStore =
                InMemoryMedicationDeletionMarkerStore()

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    markerStore = markerStore,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionResult.Failed(
                    stage =
                        MedicationDeletionStage
                            .DELETING_DATABASE_GRAPH,
                    databaseDeleted = false,
                    failure =
                        SafeAppFailure(
                            kind = AppFailureKind.CORRUPTION,
                            stage =
                                AppOperationStage
                                    .DELETING_DATABASE_GRAPH,
                            retryable = false,
                        ),
                ),
                coordinator.deleteMedication(PREVIEW),
            )

            assertEquals(PREVIEW, dataSource.graph?.preview)
            assertEquals(
                MedicationDeletionMarkerStage
                    .DATABASE_DELETE_PENDING,
                markerStore.marker.first()?.stage,
            )
        }

    @Test
    fun reconciliationFailureAfterDeletionLeavesDatabaseDeletedMarkerForRecovery() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = graph(),
                )

            val markerStore =
                InMemoryMedicationDeletionMarkerStore()

            val reminderCoordinator =
                RecordingCoreReminderCoordinator().apply {
                    reconcileAsPartialFailure = true
                }

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    markerStore = markerStore,
                    reminderCoordinator =
                        reminderCoordinator,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionResult.Failed(
                    stage =
                        MedicationDeletionStage
                            .RECONCILING_REMAINING_REMINDERS,
                    databaseDeleted = true,
                    failure =
                        SafeAppFailure(
                            kind = AppFailureKind.PLATFORM,
                            stage =
                                AppOperationStage
                                    .RECONCILING_REMINDERS,
                            retryable = true,
                        ),
                ),
                coordinator.deleteMedication(PREVIEW),
            )

            assertNull(dataSource.graph)
            assertEquals(
                MedicationDeletionMarkerStage
                    .FINAL_RECONCILIATION_PENDING,
                markerStore.marker.first()?.stage,
            )
            assertTrue(
                markerStore.clearedMedicationIds.isEmpty(),
            )
        }

    @Test
    fun recoveryAfterDatabaseDeletionUsesTargetScopedCleanupThenReconciles() =
        runTest {
            val marker =
                MedicationDeletionMarker.create(
                    expectedPreview = PREVIEW,
                    scheduleSeriesIds =
                        setOf(
                            "series-1",
                            "series-2",
                        ),
                    occurrenceIds =
                        setOf(
                            "occurrence-1",
                            "occurrence-2",
                        ),
                    stage =
                        MedicationDeletionMarkerStage
                            .DATABASE_DELETED,
                    startedAtEpochMillis = 1L,
                )

            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = null,
                )

            val markerStore =
                InMemoryMedicationDeletionMarkerStore(
                    initialMarker = marker,
                )

            val alarmGateway =
                RecordingAlarmGateway()

            val notificationGateway =
                RecordingNotificationGateway()

            val reminderCoordinator =
                RecordingCoreReminderCoordinator()

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    markerStore = markerStore,
                    alarmGateway = alarmGateway,
                    notificationGateway =
                        notificationGateway,
                    reminderCoordinator =
                        reminderCoordinator,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionRecoveryResult
                    .Completed(
                        medicationId =
                            "medication-1",
                    ),
                coordinator
                    .resumeIncompleteDeletionIfNeeded(),
            )

            assertEquals(
                setOf(
                    AlarmKey.forScheduleSeries(
                        "series-1",
                    ),
                    AlarmKey.forScheduleSeries(
                        "series-2",
                    ),
                    AlarmKey.forDelayedOccurrence(
                        "occurrence-1",
                    ),
                    AlarmKey.forDelayedOccurrence(
                        "occurrence-2",
                    ),
                ),
                alarmGateway.cancelledKeys.toSet(),
            )
            assertEquals(
                setOf("occurrence-1", "occurrence-2"),
                notificationGateway.cancelledOccurrenceIds.toSet(),
            )
            assertEquals(0, notificationGateway.cancelAllCallCount)
            assertEquals(0, reminderCoordinator.cancelAllOwnedCallCount)
            assertEquals(
                listOf(
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
                ),
                reminderCoordinator.reconcileReasons,
            )
            assertNull(markerStore.marker.first())
        }

    @Test
    fun duplicateDeletionWithoutMarkerIsSafeAndDeterministic() =
        runTest {
            val dataSource =
                FakeMedicationDeletionDataSource(
                    graph = null,
                )

            val coordinator =
                coordinator(
                    dataSource = dataSource,
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionResult.AlreadyDeleted,
                coordinator.deleteMedication(PREVIEW),
            )
            assertTrue(dataSource.deletionRequests.isEmpty())
        }

    @Test
    fun resumeWithoutMarkerDoesNothing() =
        runTest {
            val coordinator =
                coordinator(
                    dataSource =
                        FakeMedicationDeletionDataSource(
                            graph = graph(),
                        ),
                    dispatcher =
                        StandardTestDispatcher(
                            testScheduler,
                        ),
                )

            assertEquals(
                MedicationDeletionRecoveryResult
                    .NoDeletionPending,
                coordinator
                    .resumeIncompleteDeletionIfNeeded(),
            )
        }

    private fun coordinator(
        dataSource: MedicationDeletionDataSource,
        markerStore:
        InMemoryMedicationDeletionMarkerStore =
            InMemoryMedicationDeletionMarkerStore(),
        alarmGateway:
        RecordingAlarmGateway =
            RecordingAlarmGateway(),
        notificationGateway:
        RecordingNotificationGateway =
            RecordingNotificationGateway(),
        snoozeStore:
        InMemorySnoozedReminderStore =
            InMemorySnoozedReminderStore(),
        reminderCoordinator:
        RecordingCoreReminderCoordinator =
            RecordingCoreReminderCoordinator(),
        dispatcher:
        kotlinx.coroutines.CoroutineDispatcher,
    ): DefaultMedicationDeletionCoordinator =
        DefaultMedicationDeletionCoordinator(
            dataSource = dataSource,
            markerStore = markerStore,
            alarmGateway = alarmGateway,
            notificationGateway =
                notificationGateway,
            snoozedReminderStore = snoozeStore,
            reminderCoordinator =
                reminderCoordinator,
            operationGate =
                AppOperationGate(),
            clock =
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC,
                ),
            ioDispatcher = dispatcher,
        )

    private fun graph(
        preview: MedicationDeletionPreview = PREVIEW,
    ): MedicationDeletionGraph =
        MedicationDeletionGraph(
            preview = preview,
            scheduleSeriesIds =
                listOf(
                    "series-1",
                    "series-2",
                ),
            occurrenceIds =
                listOf(
                    "occurrence-1",
                    "occurrence-2",
                ),
        )

    private fun snooze(
        occurrenceId: String,
    ): SnoozedReminder =
        SnoozedReminder(
            occurrenceId = occurrenceId,
            remindAt = NOW.plusSeconds(600),
            createdAt = NOW,
        )

    private companion object {
        val NOW: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val PREVIEW =
            MedicationDeletionPreview(
                medicationId = "medication-1",
                medicationName = "داروی آزمون",
                medicationUpdatedAtEpochMillis = 1L,
                scheduleSeriesCount = 2,
                scheduleVersionCount = 3,
                scheduleTimeCount = 4,
                occurrenceCount = 2,
                caregiverReportCount = 1,
            )

        val COUNTS =
            MedicationDeletionCounts(
                caregiverReportCount = 1,
                occurrenceCount = 2,
                scheduleTimeCount = 4,
                scheduleVersionCount = 3,
                scheduleSeriesCount = 2,
                medicationCount = 1,
            )
    }
}
