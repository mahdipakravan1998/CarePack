package ir.carepack.app

import android.content.Context
import androidx.room.Room
import ir.carepack.core.id.IdSource
import ir.carepack.core.id.UuidIdSource
import ir.carepack.core.time.SystemZoneProvider
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.DatabaseMigrations
import ir.carepack.data.preferences.DataStoreReminderPreferenceStore
import ir.carepack.data.preferences.DataStoreSetupPreferenceStore
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.reminder.DefaultReminderCoordinator
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderScheduleSource
import ir.carepack.domain.reminder.RoomReminderScheduleSource
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AndroidAlarmGateway
import ir.carepack.reminder.navigation.NotificationNavigationValidator
import ir.carepack.reminder.notification.AndroidNotificationGateway
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.permission.AndroidExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.AndroidNotificationPermissionGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import java.time.Clock

class AppContainer(
    context: Context,
) {
    private val applicationContext =
        context.applicationContext

    val clock: Clock =
        Clock.systemUTC()

    val zoneProvider: ZoneProvider =
        SystemZoneProvider()

    private val idSource: IdSource =
        UuidIdSource()

    val database: CarePackDatabase =
        Room.databaseBuilder(
            applicationContext,
            CarePackDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(
                DatabaseMigrations
                    .MIGRATION_1_2,
            )
            .build()

    val setupPreferenceStore:
            SetupPreferenceStore =
        DataStoreSetupPreferenceStore(
            context =
                applicationContext,
        )

    val reminderPreferenceStore:
            ReminderPreferenceStore =
        DataStoreReminderPreferenceStore(
            context =
                applicationContext,
        )

    val notificationPermissionGateway:
            NotificationPermissionGateway =
        AndroidNotificationPermissionGateway(
            context =
                applicationContext,
        )

    val exactAlarmCapabilityGateway:
            ExactAlarmCapabilityGateway =
        AndroidExactAlarmCapabilityGateway(
            context =
                applicationContext,
        )

    val alarmGateway: AlarmGateway =
        AndroidAlarmGateway(
            context =
                applicationContext,
        )

    val notificationGateway:
            NotificationGateway =
        AndroidNotificationGateway(
            context =
                applicationContext,
        )

    val occurrenceGenerator:
            OccurrenceGenerator =
        RoomOccurrenceGenerator(
            database = database,
            idSource = idSource,
            candidateResolver =
                OccurrenceCandidateResolver(),
        )

    private val reminderScheduleSource:
            ReminderScheduleSource =
        RoomReminderScheduleSource(
            database = database,
        )

    val reminderCoordinator:
            ReminderCoordinator =
        DefaultReminderCoordinator(
            scheduleSource =
                reminderScheduleSource,
            preferenceStore =
                reminderPreferenceStore,
            notificationPermissionGateway =
                notificationPermissionGateway,
            exactAlarmCapabilityGateway =
                exactAlarmCapabilityGateway,
            alarmGateway =
                alarmGateway,
            notificationGateway =
                notificationGateway,
            clock = clock,
        )

    private val roomCarePlanService:
            CarePlanService =
        RoomCarePlanService(
            database = database,
            occurrenceGenerator =
                occurrenceGenerator,
            clock = clock,
            idSource = idSource,
        )

    val carePlanService:
            CarePlanService =
        ReminderAwareCarePlanService(
            delegate =
                roomCarePlanService,
            reminderCoordinator =
                reminderCoordinator,
        )

    private val roomCaregiverReportService:
            CaregiverReportService =
        RoomCaregiverReportService(
            database = database,
            clock = clock,
        )

    val caregiverReportService:
            CaregiverReportService =
        ReminderAwareCaregiverReportService(
            delegate =
                roomCaregiverReportService,
            reminderCoordinator =
                reminderCoordinator,
        )

    val todayQueryService:
            TodayQueryService =
        RoomTodayQueryService(
            database = database,
        )

    val notificationNavigationValidator =
        NotificationNavigationValidator(
            database = database,
        )

    val appReconciler =
        AppReconciler(
            occurrenceGenerator =
                occurrenceGenerator,
            reminderCoordinator =
                reminderCoordinator,
            reminderPreferenceStore =
                reminderPreferenceStore,
            clock = clock,
            zoneProvider =
                zoneProvider,
        )

    private companion object {
        const val DATABASE_NAME =
            "carepack.db"
    }
}
