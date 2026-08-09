package ir.carepack.settings.deletion

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.CarePackApplication
import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.id.IdSource
import ir.carepack.data.preferences.DataStoreDataDeletionMarkerStore
import ir.carepack.data.preferences.DataStorePreferenceDataCleaner
import ir.carepack.data.preferences.carePackDataStore
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataDeletionIntegrationTest {

    private lateinit var context: Context
    private lateinit var application: CarePackApplication

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            application = context.applicationContext as CarePackApplication
            resetState()
        }

    @After
    fun tearDown() =
        runBlocking {
            resetState()
        }

    @Test
    fun deleteEverything_clearsRoomPreferencesAndOperationMarker() =
        runBlocking {
            seedRecipient()
            application.container
                .privacyPreferenceStore
                .setIncludeRecipientName(true)
            application.container
                .reminderPreferenceStore
                .setRemindersEnabled(true)

            val result =
                application.container
                    .dataDeletionCoordinator
                    .deleteEverything()

            assertEquals(DataDeletionResult.Completed, result)
            assertEquals(
                0,
                application.container.database
                    .careRecipientDao()
                    .count(),
            )
            assertEquals(
                false,
                application.container
                    .privacyPreferenceStore
                    .state
                    .first()
                    .includeRecipientName,
            )
            assertEquals(
                false,
                application.container
                    .reminderPreferenceStore
                    .state
                    .first()
                    .remindersEnabled,
            )
            assertEquals(
                DataDeletionMarkerReadResult.Absent,
                DataStoreDataDeletionMarkerStore(context)
                    .state
                    .first(),
            )
        }

    @Test
    fun recoveryFromDomainDataPending_resumesWithoutForegroundActivity() =
        runBlocking {
            seedRecipient()
            val markerStore = DataStoreDataDeletionMarkerStore(context)
            markerStore.save(
                DataDeletionMarker.create(
                    operationId = "recovery-operation",
                    stage = DataDeletionMarkerStage.DOMAIN_DATA_PENDING,
                    startedAtEpochMillis =
                        Instant.parse("2026-06-24T08:00:00Z")
                            .toEpochMilli(),
                ),
            )

            val coordinator =
                DefaultDataDeletionCoordinator(
                    markerStore = markerStore,
                    reminderCoordinator =
                        application.container.reminderCoordinator,
                    notificationGateway =
                        application.container.notificationGateway,
                    domainDataCleaner =
                        RoomDomainDataCleaner(
                            application.container.database,
                        ),
                    preferenceDataCleaner =
                        DataStorePreferenceDataCleaner(context),
                    temporaryDataCleaner =
                        AndroidTemporaryDataCleaner(context),
                    auxiliaryDeletionStateCleaner =
                        AuxiliaryDeletionStateCleaner {
                            application.container
                                .reminderTestCoordinator
                                .cancelPendingTest()
                            application.container
                                .systemReconciliationRetryScheduler
                                .clearAll()
                        },
                    operationGate = AppOperationGate(),
                    idSource = IdSource { "unused-operation" },
                    clock =
                        Clock.fixed(
                            Instant.parse("2026-06-24T08:00:00Z"),
                            ZoneOffset.UTC,
                        ),
                )

            val result =
                coordinator.resumeIncompleteDeletionIfNeeded()

            assertEquals(DataDeletionResult.Completed, result)
            assertEquals(
                0,
                application.container.database
                    .careRecipientDao()
                    .count(),
            )
            assertEquals(
                DataDeletionMarkerReadResult.Absent,
                markerStore.state.first(),
            )
        }

    private suspend fun seedRecipient() {
        val outcome =
            application.container.carePlanService
                .createRecipient(
                    CreateRecipientCommand(
                        displayName = "فرد آزمون حذف کامل",
                    ),
                )

        assertTrue(
            outcome is CreateRecipientOutcome.Created ||
                outcome is CreateRecipientOutcome.AlreadyExists,
        )
    }

    private suspend fun resetState() {
        application.container.database.clearAllTables()
        context.carePackDataStore.edit { it.clear() }
        application.container.notificationGateway.cancelAll()
        application.container.reminderCoordinator
            .cancelAllOwnedReminderState()
    }
}
