package ir.carepack.app

import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.occurrence.GenerationSummary
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.TimezoneObservation
import java.time.Clock

data class AppReconciliationResult(
    val generationSummary: GenerationSummary,
    val timezoneObservation:
    TimezoneObservation,
    val reminderResult:
    ReminderReconciliationResult,
)

class AppReconciler(
    private val occurrenceGenerator:
    OccurrenceGenerator,
    private val reminderCoordinator:
    ReminderCoordinator,
    private val reminderPreferenceStore:
    ReminderPreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) {

    suspend fun reconcile(
        reason: ReconciliationReason,
    ): AppReconciliationResult {
        val now =
            clock.instant()

        val deviceZone =
            zoneProvider.currentZone()

        val anchorDate =
            now
                .atZone(deviceZone)
                .toLocalDate()

        val generationSummary =
            occurrenceGenerator
                .guaranteeWindowForAll(
                    anchorDate =
                        anchorDate,
                    now = now,
                )

        val timezoneObservation =
            reminderPreferenceStore
                .observeDeviceZone(
                    zoneId =
                        deviceZone.id,
                )

        val reminderResult =
            reminderCoordinator
                .reconcile(
                    reason = reason,
                )

        return AppReconciliationResult(
            generationSummary =
                generationSummary,
            timezoneObservation =
                timezoneObservation,
            reminderResult =
                reminderResult,
        )
    }
}
