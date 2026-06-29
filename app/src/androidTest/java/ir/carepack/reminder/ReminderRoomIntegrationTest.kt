package ir.carepack.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.AppReconciler
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.DefaultReminderCoordinator
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.RoomReminderScheduleSource
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.reminder.TimezoneWarning
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRoomIntegrationTest {

    private lateinit var fixture:
            CarePlanRoomTestFixture

    @Before
    fun setUp() {
        fixture =
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        INITIAL_INSTANT,
                    idPrefix =
                        "reminder-room-id",
                )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun queryReturnsOnlyEarliestFutureUnreportedOccurrencePerActiveSeries() =
        runBlocking {
            val firstPlan =
                fixture.createPlan(
                    medicationName =
                        "داروی اول",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            12 * 60,
                            14 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                    zoneId =
                        "Asia/Tehran",
                )

            val secondPlan =
                fixture.createPlan(
                    medicationName =
                        "داروی دوم",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            13 * 60,
                            15 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                    zoneId =
                        "Asia/Tehran",
                )

            val source =
                RoomReminderScheduleSource(
                    database =
                        fixture.database,
                )

            val targets =
                source.getNextEligibleTargets(
                    now =
                        fixture.clock.instant(),
                )

            assertEquals(
                2,
                targets.size,
            )

            val targetsBySeries =
                targets.associateBy {
                    it.alarmKey
                        .scheduleSeriesId
                }

            assertEquals(
                12 * 60,
                targetsBySeries
                    .getValue(
                        firstPlan
                            .scheduleSeriesId,
                    )
                    .localTime
                    .toSecondOfDay() /
                        60,
            )

            assertEquals(
                13 * 60,
                targetsBySeries
                    .getValue(
                        secondPlan
                            .scheduleSeriesId,
                    )
                    .localTime
                    .toSecondOfDay() /
                        60,
            )
        }

    @Test
    fun reportedCancelledAndStoppedOccurrences_areExcluded() =
        runBlocking {
            val reportedPlan =
                fixture.createPlan(
                    medicationName =
                        "داروی گزارش‌شده",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            12 * 60,
                            14 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                )

            val reportedOccurrences =
                fixture.database
                    .occurrenceDao()
                    .getForVersion(
                        reportedPlan
                            .scheduleVersionId,
                    )

            val firstReportedOccurrence =
                reportedOccurrences
                    .single {
                        it.minuteOfDay ==
                                12 * 60
                    }

            fixture.reportService
                .setReport(
                    occurrenceId =
                        firstReportedOccurrence
                            .id,
                    newState =
                        CaregiverReportState
                            .GIVEN,
                )

            val cancelledPlan =
                fixture.createPlan(
                    medicationName =
                        "داروی لغوشده",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                )

            fixture.database
                .occurrenceDao()
                .cancelFutureUnreportedForVersion(
                    scheduleVersionId =
                        cancelledPlan
                            .scheduleVersionId,
                    nowEpochMillis =
                        fixture
                            .clock
                            .instant()
                            .minusSeconds(1L)
                            .toEpochMilli(),
                    cancelledAtEpochMillis =
                        fixture
                            .clock
                            .instant()
                            .toEpochMilli(),
                    cancellationReason =
                        OccurrenceCancellationReason
                            .SCHEDULE_REPLACED
                            .name,
                )

            val stoppedPlan =
                fixture.createPlan(
                    medicationName =
                        "داروی متوقف‌شده",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                )

            fixture.carePlanService
                .stopMedication(
                    medicationId =
                        stoppedPlan
                            .medicationId,
                )

            val source =
                RoomReminderScheduleSource(
                    database =
                        fixture.database,
                )

            val targets =
                source.getNextEligibleTargets(
                    now =
                        fixture.clock.instant(),
                )

            assertEquals(
                1,
                targets.size,
            )

            val remainingTarget =
                targets.single()

            assertEquals(
                reportedPlan
                    .scheduleSeriesId,
                remainingTarget
                    .alarmKey
                    .scheduleSeriesId,
            )

            assertEquals(
                14 * 60,
                remainingTarget
                    .localTime
                    .toSecondOfDay() /
                        60,
            )

            assertFalse(
                targets.any {
                    it.alarmKey
                        .scheduleSeriesId ==
                            cancelledPlan
                                .scheduleSeriesId
                },
            )

            assertFalse(
                targets.any {
                    it.alarmKey
                        .scheduleSeriesId ==
                            stoppedPlan
                                .scheduleSeriesId
                },
            )
        }

    @Test
    fun alarmFire_postsNotification_withoutCreatingCaregiverReport() =
        runBlocking {
            val plan =
                fixture.createPlan(
                    medicationName =
                        "داروی خصوصی",
                    instruction =
                        "دستور محرمانه",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                )

            val occurrence =
                fixture.database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )
                    .single()

            fixture.moveTo(
                Instant.ofEpochMilli(
                    occurrence
                        .scheduledAtEpochMillis,
                ),
            )

            val notificationGateway =
                IntegrationNotificationGateway()

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        IntegrationReminderPreferenceStore(
                            initialState =
                                ReminderPreferenceState(
                                    remindersEnabled =
                                        true,
                                ),
                        ),
                    notificationGateway =
                        notificationGateway,
                )

            val result =
                coordinator.handleAlarmFired(
                    occurrenceId =
                        occurrence.id,
                )

            assertTrue(
                result is
                        AlarmFireResult
                        .NotificationPosted,
            )

            assertEquals(
                occurrence.id,
                notificationGateway
                    .notifications
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                "داروی خصوصی",
                notificationGateway
                    .notifications
                    .single()
                    .medicationName,
            )

            assertNull(
                fixture.database
                    .reportingDao()
                    .getReport(
                        occurrence.id,
                    ),
            )

            assertTrue(
                fixture.database
                    .occurrenceDao()
                    .getById(
                        occurrence.id,
                    ) != null,
            )
        }

    @Test
    fun timezoneReconciliation_preservesStoredZoneSnapshotsAndReports() =
        runBlocking {
            val plan =
                fixture.createPlan(
                    medicationName =
                        "داروی منطقه ثابت",
                    weekdays =
                        setOf(
                            DayOfWeek
                                .WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        ANCHOR_DATE,
                    endDate =
                        ANCHOR_DATE,
                    zoneId =
                        "Asia/Tehran",
                )

            val originalOccurrence =
                fixture.database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )
                    .single()

            fixture.reportService
                .setReport(
                    occurrenceId =
                        originalOccurrence.id,
                    newState =
                        CaregiverReportState
                            .UNKNOWN,
                )

            val originalReport =
                fixture.database
                    .reportingDao()
                    .getReport(
                        originalOccurrence.id,
                    )

            val preferenceStore =
                IntegrationReminderPreferenceStore(
                    initialState =
                        ReminderPreferenceState(
                            remindersEnabled =
                                false,
                        ),
                )

            preferenceStore.observeDeviceZone(
                zoneId =
                    "Asia/Tehran",
            )

            val coordinator =
                createCoordinator(
                    preferenceStore =
                        preferenceStore,
                    notificationGateway =
                        IntegrationNotificationGateway(),
                )

            val reconciler =
                AppReconciler(
                    occurrenceGenerator =
                        fixture
                            .occurrenceGenerator,
                    reminderCoordinator =
                        coordinator,
                    reminderPreferenceStore =
                        preferenceStore,
                    clock =
                        fixture.clock,
                    zoneProvider =
                        ZoneProvider {
                            ZoneId.of(
                                "Europe/Berlin",
                            )
                        },
                )

            val result =
                reconciler.reconcile(
                    reason =
                        ReconciliationReason
                            .TIMEZONE_CHANGED,
                )

            assertTrue(
                result.timezoneObservation is
                        TimezoneObservation
                        .Changed,
            )

            val storedDefinition =
                fixture.database
                    .scheduleDao()
                    .getDefinitionsForVersion(
                        plan.scheduleVersionId,
                    )
                    .single()

            assertEquals(
                "Asia/Tehran",
                storedDefinition.zoneId,
            )

            val persistedOccurrence =
                fixture.database
                    .occurrenceDao()
                    .getById(
                        originalOccurrence.id,
                    )

            assertEquals(
                originalOccurrence,
                persistedOccurrence,
            )

            assertEquals(
                originalReport,
                fixture.database
                    .reportingDao()
                    .getReport(
                        originalOccurrence.id,
                    ),
            )

            assertEquals(
                TimezoneWarning(
                    previousZoneId =
                        "Asia/Tehran",
                    currentZoneId =
                        "Europe/Berlin",
                ),
                preferenceStore
                    .state
                    .first()
                    .timezoneWarning,
            )
        }

    private fun createCoordinator(
        preferenceStore:
        ReminderPreferenceStore,
        notificationGateway:
        NotificationGateway,
    ): ReminderCoordinator {
        return DefaultReminderCoordinator(
            scheduleSource =
                RoomReminderScheduleSource(
                    database =
                        fixture.database,
                ),
            preferenceStore =
                preferenceStore,
            notificationPermissionGateway =
                object :
                    NotificationPermissionGateway {

                    override fun isPermissionGranted():
                            Boolean {
                        return true
                    }

                    override fun requiresRuntimePermission():
                            Boolean {
                        return true
                    }
                },
            exactAlarmCapabilityGateway =
                object :
                    ExactAlarmCapabilityGateway {

                    override fun canScheduleExactAlarms():
                            Boolean {
                        return false
                    }
                },
            alarmGateway =
                IntegrationAlarmGateway(),
            notificationGateway =
                notificationGateway,
            clock =
                fixture.clock,
        )
    }

    private companion object {
        val INITIAL_INSTANT: Instant =
            Instant.parse(
                "2026-06-24T06:00:00Z",
            )

        val ANCHOR_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}

private class IntegrationAlarmGateway :
    AlarmGateway {

    val scheduledRequests =
        mutableListOf<AlarmRequest>()

    override fun schedule(
        request: AlarmRequest,
    ) {
        scheduledRequests +=
            request
    }

    override fun cancel(
        alarmKey:
        ir.carepack.domain.reminder.AlarmKey,
    ) {
        Unit
    }
}

private class IntegrationNotificationGateway :
    NotificationGateway {

    val notifications =
        mutableListOf<ReminderNotification>()

    override fun post(
        notification:
        ReminderNotification,
    ) {
        notifications +=
            notification
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        Unit
    }

    override fun cancelAll() {
        Unit
    }
}

private class IntegrationReminderPreferenceStore(
    initialState:
    ReminderPreferenceState,
) : ReminderPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<ReminderPreferenceState> =
        mutableState

    override suspend fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    remindersEnabled =
                        enabled,
                )
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation {
        val previousZoneId =
            mutableState
                .value
                .lastObservedZoneId

        return when {
            previousZoneId == null -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            lastObservedZoneId =
                                zoneId,
                        )

                TimezoneObservation.Initialized
            }

            previousZoneId == zoneId -> {
                TimezoneObservation.Unchanged
            }

            else -> {
                val warning =
                    TimezoneWarning(
                        previousZoneId =
                            previousZoneId,
                        currentZoneId =
                            zoneId,
                    )

                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            lastObservedZoneId =
                                zoneId,
                            timezoneWarning =
                                warning,
                        )

                TimezoneObservation
                    .Changed(warning)
            }
        }
    }

    override suspend fun dismissTimezoneWarning() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    timezoneWarning = null,
                )
    }
}
