package ir.carepack.app

import android.content.Context
import androidx.room.Room
import ir.carepack.BuildConfig
import ir.carepack.core.id.IdSource
import ir.carepack.core.id.UuidIdSource
import ir.carepack.core.time.SystemZoneProvider
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.preferences.DataStoreDataDeletionMarkerStore
import ir.carepack.data.preferences.DataStoreMedicationDeletionMarkerStore
import ir.carepack.data.preferences.DataStorePreferenceDataCleaner
import ir.carepack.data.preferences.DataStorePrivacyPreferenceStore
import ir.carepack.data.preferences.DataStoreReminderPreferenceStore
import ir.carepack.data.preferences.DataStoreSetupPreferenceStore
import ir.carepack.data.preferences.DataStoreSnoozedReminderStore
import ir.carepack.data.preferences.DataStoreUserExperiencePreferenceStore
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.data.service.RoomCarePlanService
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.data.service.RoomOccurrenceGenerator
import ir.carepack.domain.reminder.DefaultReminderCoordinator
import ir.carepack.domain.reminder.DefaultReminderTestCoordinator
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderDiagnosticSink
import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderScheduleSource
import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.data.service.RoomReminderScheduleSource
import ir.carepack.domain.reminder.SnoozedReminderStore
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.data.service.RoomCaregiverReportService
import ir.carepack.data.service.RoomDateRangeSummaryService
import ir.carepack.data.service.RoomRangeReportFormatter
import ir.carepack.data.service.RoomTodayReportFormatter
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.data.service.RoomTodayQueryService
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AndroidAlarmGateway
import ir.carepack.reminder.diagnostic.LogcatReminderDiagnosticSink
import ir.carepack.reminder.navigation.NotificationNavigationValidator
import ir.carepack.reminder.notification.AndroidNotificationGateway
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.receiver.SystemReconciliationRetryScheduler
import ir.carepack.reminder.permission.AndroidExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.AndroidNotificationPermissionGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.reporting.share.AndroidTextShareGateway
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.settings.deletion.AndroidTemporaryDataCleaner
import ir.carepack.settings.deletion.AuxiliaryDeletionStateCleaner
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionMarkerStore
import ir.carepack.settings.deletion.DefaultDataDeletionCoordinator
import ir.carepack.settings.deletion.DefaultMedicationDeletionCoordinator
import ir.carepack.settings.deletion.DomainDataCleaner
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionDataSource
import ir.carepack.settings.deletion.PreferenceDataCleaner
import ir.carepack.settings.deletion.MedicationDeletionMarkerStore
import ir.carepack.settings.deletion.RoomDomainDataCleaner
import ir.carepack.settings.deletion.RoomMedicationDeletionDataSource
import ir.carepack.settings.deletion.TemporaryDataCleaner
import java.time.Clock

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val clock: Clock = Clock.systemUTC()

    val zoneProvider: ZoneProvider = SystemZoneProvider()

    val systemReconciliationRetryScheduler = SystemReconciliationRetryScheduler(
            context = applicationContext,
            clock = clock,
        )

    private val idSource: IdSource = UuidIdSource()

    private val reminderOperationLock = AppOperationGate()

    val reminderDiagnosticSink: ReminderDiagnosticSink =
        LogcatReminderDiagnosticSink(
            enabled = BuildConfig.DEBUG,
        )

    val database: CarePackDatabase = Room.databaseBuilder(
            applicationContext,
            CarePackDatabase::class.java,
            DATABASE_NAME,
        ).addCallback(
                CarePackDatabase.invariantCallback,
            ).build()

    val setupPreferenceStore: SetupPreferenceStore =
        DataStoreSetupPreferenceStore(
            context = applicationContext,
        )

    val reminderPreferenceStore: ReminderPreferenceStore =
        DataStoreReminderPreferenceStore(
            context = applicationContext,
        )

    val privacyPreferenceStore: PrivacyPreferenceStore =
        DataStorePrivacyPreferenceStore(
            context = applicationContext,
        )

    val userExperiencePreferenceStore: UserExperiencePreferenceStore =
        DataStoreUserExperiencePreferenceStore(
            context = applicationContext,
        )

    val snoozedReminderStore: SnoozedReminderStore =
        DataStoreSnoozedReminderStore(
            context = applicationContext,
        )

    private val medicationDeletionMarkerStore: MedicationDeletionMarkerStore =
        DataStoreMedicationDeletionMarkerStore(
            context = applicationContext,
        )

    private val dataDeletionMarkerStore: DataDeletionMarkerStore =
        DataStoreDataDeletionMarkerStore(
            context = applicationContext,
        )

    private val preferenceDataCleaner: PreferenceDataCleaner =
        DataStorePreferenceDataCleaner(
            context = applicationContext,
        )

    val notificationPermissionGateway: NotificationPermissionGateway =
        AndroidNotificationPermissionGateway(
            context = applicationContext,
        )

    val exactAlarmCapabilityGateway: ExactAlarmCapabilityGateway =
        AndroidExactAlarmCapabilityGateway(
            context = applicationContext,
        )

    private val androidAlarmGateway = AndroidAlarmGateway(
            context = applicationContext,
            clock = clock,
            diagnosticSink = reminderDiagnosticSink,
        )

    val alarmGateway: AlarmGateway = androidAlarmGateway

    private val androidNotificationGateway = AndroidNotificationGateway(
            context = applicationContext,
            clock = clock,
            diagnosticSink = reminderDiagnosticSink,
        )

    val notificationGateway: NotificationGateway =
        androidNotificationGateway

    val occurrenceGenerator: OccurrenceGenerator =
        RoomOccurrenceGenerator(
            database = database,
            idSource = idSource,
            candidateResolver = OccurrenceCandidateResolver(),
        )

    private val reminderScheduleSource: ReminderScheduleSource =
        RoomReminderScheduleSource(
            database = database,
        )

    val reminderCoordinator: ReminderCoordinator =
        DefaultReminderCoordinator(
            scheduleSource = reminderScheduleSource,
            preferenceStore = reminderPreferenceStore,
            snoozedReminderStore = snoozedReminderStore,
            notificationPermissionGateway = notificationPermissionGateway,
            exactAlarmCapabilityGateway = exactAlarmCapabilityGateway,
            alarmGateway = alarmGateway,
            notificationGateway = notificationGateway,
            clock = clock,
            diagnosticSink = reminderDiagnosticSink,
            operationLock = reminderOperationLock,
        )

    val reminderTestCoordinator: ReminderTestCoordinator =
        DefaultReminderTestCoordinator(
            notificationPermissionGateway = notificationPermissionGateway,
            exactAlarmCapabilityGateway = exactAlarmCapabilityGateway,
            alarmGateway = androidAlarmGateway,
            notificationGateway = androidNotificationGateway,
            clock = clock,
            operationLock = reminderOperationLock,
        )

    private val roomCarePlanService: CarePlanService =
        RoomCarePlanService(
            database = database,
            occurrenceGenerator = occurrenceGenerator,
            clock = clock,
            idSource = idSource,
        )

    val carePlanService: CarePlanService =
        ReminderAwareCarePlanService(
            delegate = roomCarePlanService,
            reminderCoordinator = reminderCoordinator,
            reminderPreferenceStore = reminderPreferenceStore,
            operationGate = reminderOperationLock,
            clock = clock,
        )

    private val roomCaregiverReportService: CaregiverReportService =
        RoomCaregiverReportService(
            database = database,
            clock = clock,
        )

    val caregiverReportService: CaregiverReportService =
        ReminderAwareCaregiverReportService(
            delegate = roomCaregiverReportService,
            reminderCoordinator = reminderCoordinator,
            reminderPreferenceStore = reminderPreferenceStore,
            operationGate = reminderOperationLock,
            clock = clock,
        )

    val todayQueryService: TodayQueryService =
        RoomTodayQueryService(
            database = database,
        )

    val todayReportFormatter: TodayReportFormatter =
        RoomTodayReportFormatter(
            database = database,
        )

    val dateRangeSummaryService: DateRangeSummaryService =
        RoomDateRangeSummaryService(
            database = database,
        )

    val rangeReportFormatter: RangeReportFormatter =
        RoomRangeReportFormatter(
            database = database,
            summaryService = dateRangeSummaryService,
        )

    val textShareGateway: TextShareGateway =
        AndroidTextShareGateway(
            context = applicationContext,
        )

    private val medicationDeletionDataSource: MedicationDeletionDataSource =
        RoomMedicationDeletionDataSource(
            database = database,
        )

    val medicationDeletionCoordinator: MedicationDeletionCoordinator =
        DefaultMedicationDeletionCoordinator(
            dataSource = medicationDeletionDataSource,
            markerStore = medicationDeletionMarkerStore,
            alarmGateway = alarmGateway,
            notificationGateway = notificationGateway,
            snoozedReminderStore = snoozedReminderStore,
            reminderCoordinator = reminderCoordinator,
            operationGate = reminderOperationLock,
            clock = clock,
        )

    private val domainDataCleaner: DomainDataCleaner =
        RoomDomainDataCleaner(
            database = database,
        )

    private val temporaryDataCleaner: TemporaryDataCleaner =
        AndroidTemporaryDataCleaner(
            context = applicationContext,
        )

    private val auxiliaryDeletionStateCleaner = AuxiliaryDeletionStateCleaner {
            reminderTestCoordinator.cancelPendingTest()
            systemReconciliationRetryScheduler.clearAll()
        }

    val dataDeletionCoordinator: DataDeletionCoordinator =
        DefaultDataDeletionCoordinator(
            markerStore = dataDeletionMarkerStore,
            reminderCoordinator = reminderCoordinator,
            notificationGateway = notificationGateway,
            domainDataCleaner = domainDataCleaner,
            preferenceDataCleaner = preferenceDataCleaner,
            temporaryDataCleaner = temporaryDataCleaner,
            auxiliaryDeletionStateCleaner = auxiliaryDeletionStateCleaner,
            operationGate = reminderOperationLock,
            idSource = idSource,
            clock = clock,
        )

    val notificationNavigationValidator = NotificationNavigationValidator(
            database = database,
        )

    val appReconciler = AppReconciler(
            medicationDeletionCoordinator = medicationDeletionCoordinator,
            dataDeletionCoordinator = dataDeletionCoordinator,
            occurrenceGenerator = occurrenceGenerator,
            reminderCoordinator = reminderCoordinator,
            reminderPreferenceStore = reminderPreferenceStore,
            clock = clock,
            zoneProvider = zoneProvider,
            operationGate = reminderOperationLock,
        )

    private companion object {
        const val DATABASE_NAME = "carepack.db"
    }
}
