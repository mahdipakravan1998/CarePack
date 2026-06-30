package ir.carepack.settings.deletion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.preferences.DataStorePrivacyPreferenceStore
import ir.carepack.data.preferences.DataStoreReminderPreferenceStore
import ir.carepack.data.preferences.DataStoreSetupPreferenceStore
import ir.carepack.data.preferences.PrivacyPreferenceState
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.RecordingNotificationGateway
import ir.carepack.testing.RecordingReminderCoordinator
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataDeletionIntegrationTest {

    private val context:
            Context =
        ApplicationProvider
            .getApplicationContext()

    private lateinit var fixture:
            CarePlanRoomTestFixture

    private lateinit var privacyStore:
            DataStorePrivacyPreferenceStore

    private lateinit var setupStore:
            DataStoreSetupPreferenceStore

    private lateinit var reminderStore:
            DataStoreReminderPreferenceStore

    @Before
    fun setUp() =
        runBlocking {
            privacyStore =
                DataStorePrivacyPreferenceStore(
                    context = context,
                )

            setupStore =
                DataStoreSetupPreferenceStore(
                    context = context,
                )

            reminderStore =
                DataStoreReminderPreferenceStore(
                    context = context,
                )

            clearAllPreferences()

            fixture =
                CarePlanRoomTestFixture.create(
                    initialInstant =
                        INITIAL_INSTANT,
                    idPrefix =
                        "deletion-contract",
                    clockZone =
                        ZoneOffset.UTC,
                    context = context,
                )
        }

    @After
    fun tearDown() =
        runBlocking {
            fixture.close()
            clearAllPreferences()
            removeTestTemporaryFiles()
        }

    @Test
    fun deleteEverything_clearsDomainPreferencesPlatformStateAndTemporaryFiles() =
        runBlocking {
            populateAllState()
            val temporaryFiles =
                createTestTemporaryFiles()

            val reminderCoordinator =
                RecordingReminderCoordinator()

            val notificationGateway =
                RecordingNotificationGateway()

            val coordinator =
                createCoordinator(
                    reminderCoordinator =
                        reminderCoordinator,
                    notificationGateway =
                        notificationGateway,
                    temporaryDataCleaner =
                        AndroidTemporaryDataCleaner(
                            context = context,
                        ),
                )

            val result =
                coordinator
                    .deleteEverything()

            assertEquals(
                DataDeletionResult.Completed,
                result,
            )

            assertDatabaseEmpty()

            assertFalse(
                setupStore
                    .setupComplete
                    .first(),
            )

            assertFalse(
                reminderStore
                    .state
                    .first()
                    .remindersEnabled,
            )

            assertEquals(
                PrivacyPreferenceState(),
                privacyStore
                    .state
                    .first(),
            )

            temporaryFiles.forEach {
                    file ->
                assertFalse(
                    file.exists(),
                )
            }

            assertEquals(
                1,
                reminderCoordinator
                    .cancelAllCount,
            )

            assertEquals(
                1,
                notificationGateway
                    .cancelAllCount,
            )
        }

    @Test
    fun failedCleanup_keepsMarkerAndNextStartupResumeCompletesDeletion() =
        runBlocking {
            populateAllState()
            val temporaryFiles =
                createTestTemporaryFiles()

            val reminderCoordinator =
                RecordingReminderCoordinator()

            val notificationGateway =
                RecordingNotificationGateway()

            val failingCoordinator =
                createCoordinator(
                    reminderCoordinator =
                        reminderCoordinator,
                    notificationGateway =
                        notificationGateway,
                    temporaryDataCleaner =
                        TemporaryDataCleaner {
                            throw IOException(
                                "Injected temporary cleanup failure.",
                            )
                        },
                )

            val failedResult =
                failingCoordinator
                    .deleteEverything()

            assertEquals(
                DataDeletionResult.Failed(
                    stage =
                        DataDeletionStage
                            .CLEARING_TEMPORARY_DATA,
                ),
                failedResult,
            )

            assertTrue(
                privacyStore
                    .state
                    .first()
                    .deletionInProgress,
            )

            assertDatabaseEmpty()

            assertTrue(
                temporaryFiles.any {
                    it.exists()
                },
            )

            val recoveryCoordinator =
                createCoordinator(
                    reminderCoordinator =
                        reminderCoordinator,
                    notificationGateway =
                        notificationGateway,
                    temporaryDataCleaner =
                        AndroidTemporaryDataCleaner(
                            context = context,
                        ),
                )

            assertEquals(
                DataDeletionResult.Completed,
                recoveryCoordinator
                    .resumeIncompleteDeletionIfNeeded(),
            )

            assertEquals(
                PrivacyPreferenceState(),
                privacyStore
                    .state
                    .first(),
            )

            temporaryFiles.forEach {
                    file ->
                assertFalse(
                    file.exists(),
                )
            }

            assertEquals(
                DataDeletionResult
                    .NoDeletionPending,
                recoveryCoordinator
                    .resumeIncompleteDeletionIfNeeded(),
            )
        }

    private suspend fun populateAllState() {
        val recipientId =
            fixture.createOrGetRecipient(
                displayName =
                    "فرد حذف آزمایشی",
            )

        val plan =
            fixture.createPlan(
                recipientId =
                    recipientId,
                medicationName =
                    "داروی حذف آزمایشی",
                instruction =
                    "دستور حذف آزمایشی",
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                minutesOfDay =
                    listOf(8 * 60),
                startDate =
                    ANCHOR_DATE,
                endDate =
                    ANCHOR_DATE,
            )

        val occurrence =
            fixture.occurrenceForDate(
                scheduleVersionId =
                    plan.scheduleVersionId,
                date =
                    ANCHOR_DATE,
            )

        fixture.reportService.setReport(
            occurrenceId =
                occurrence.id,
            newState =
                CaregiverReportState.GIVEN,
        )

        setupStore.markSetupComplete()

        reminderStore.setRemindersEnabled(
            enabled = true,
        )

        privacyStore.setIncludeRecipientName(
            includeRecipientName = true,
        )

        assertTrue(
            fixture
                .database
                .careRecipientDao()
                .count() > 0,
        )

        assertTrue(
            fixture
                .database
                .medicationDao()
                .count() > 0,
        )

        assertTrue(
            fixture
                .database
                .occurrenceDao()
                .count() > 0,
        )

        assertTrue(
            fixture
                .database
                .reportingDao()
                .countReports() > 0,
        )
    }

    private fun createCoordinator(
        reminderCoordinator:
        RecordingReminderCoordinator,
        notificationGateway:
        RecordingNotificationGateway,
        temporaryDataCleaner:
        TemporaryDataCleaner,
    ): DefaultDataDeletionCoordinator =
        DefaultDataDeletionCoordinator(
            privacyPreferenceStore =
                privacyStore,
            reminderCoordinator =
                reminderCoordinator,
            notificationGateway =
                notificationGateway,
            domainDataCleaner =
                RoomDomainDataCleaner(
                    database =
                        fixture.database,
                ),
            temporaryDataCleaner =
                temporaryDataCleaner,
            ioDispatcher =
                Dispatchers.IO,
        )

    private suspend fun assertDatabaseEmpty() {
        assertEquals(
            0,
            fixture
                .database
                .reportingDao()
                .countReports(),
        )

        assertEquals(
            0,
            fixture
                .database
                .occurrenceDao()
                .count(),
        )

        assertEquals(
            0,
            fixture
                .database
                .scheduleDao()
                .countTimes(),
        )

        assertEquals(
            0,
            fixture
                .database
                .scheduleDao()
                .countVersions(),
        )

        assertEquals(
            0,
            fixture
                .database
                .scheduleDao()
                .countSeries(),
        )

        assertEquals(
            0,
            fixture
                .database
                .medicationDao()
                .count(),
        )

        assertEquals(
            0,
            fixture
                .database
                .careRecipientDao()
                .count(),
        )
    }

    private fun createTestTemporaryFiles():
            List<File> {
        val cacheFile =
            File(
                context.cacheDir,
                "carepack-deletion-cache.tmp",
            )

        val temporaryFile =
            File(
                File(
                    context.filesDir,
                    "carepack-temporary",
                ),
                "draft.tmp",
            )

        val previewFile =
            File(
                File(
                    context.filesDir,
                    "carepack-report-previews",
                ),
                "preview.tmp",
            )

        listOf(
            cacheFile,
            temporaryFile,
            previewFile,
        )
            .forEach { file ->
                file.parentFile
                    ?.mkdirs()

                file.writeText(
                    "synthetic test content",
                )
            }

        return listOf(
            cacheFile,
            temporaryFile,
            previewFile,
        )
    }

    private fun removeTestTemporaryFiles() {
        File(
            context.cacheDir,
            "carepack-deletion-cache.tmp",
        )
            .delete()

        File(
            context.filesDir,
            "carepack-temporary",
        )
            .deleteRecursively()

        File(
            context.filesDir,
            "carepack-report-previews",
        )
            .deleteRecursively()
    }

    private suspend fun clearAllPreferences() {
        privacyStore.markDeletionInProgress()
        privacyStore.clearAllPreservingDeletionMarker()
        privacyStore.completeDeletion()
    }

    private companion object {

        val INITIAL_INSTANT:
                Instant =
            Instant.parse(
                "2026-06-24T00:00:00Z",
            )

        val ANCHOR_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )
    }
}
