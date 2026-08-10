package ir.carepack.app

import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import java.time.Clock

/** App-shell dependencies; feature factories continue to receive narrow explicit inputs. */
data class CarePackUiDependencies(
    val carePlanService: CarePlanService,
    val todayQueryService: TodayQueryService,
    val caregiverReportService: CaregiverReportService,
    val setupPreferenceStore: SetupPreferenceStore,
    val reminderPreferenceStore: ReminderPreferenceStore,
    val reminderCoordinator: ReminderCoordinator,
    val reminderTestCoordinator: ReminderTestCoordinator,
    val notificationPermissionGateway: NotificationPermissionGateway,
    val todayReportFormatter: TodayReportFormatter,
    val dateRangeSummaryService: DateRangeSummaryService,
    val rangeReportFormatter: RangeReportFormatter,
    val privacyPreferenceStore: PrivacyPreferenceStore,
    val userExperiencePreferenceStore: UserExperiencePreferenceStore,
    val textShareGateway: TextShareGateway,
    val dataDeletionCoordinator: DataDeletionCoordinator,
    val medicationDeletionCoordinator: MedicationDeletionCoordinator,
    val clock: Clock,
    val zoneProvider: ZoneProvider,
)
