package ir.carepack.testing

import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.reminder.TimezoneWarning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryReminderPreferenceStore(
    initialState: ReminderPreferenceState =
        ReminderPreferenceState(),
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
        mutableState.update {
            it.copy(
                remindersEnabled =
                    enabled,
            )
        }
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation {
        val current =
            mutableState.value

        val previous =
            current.lastObservedZoneId

        return when {
            previous == null -> {
                mutableState.value =
                    current.copy(
                        lastObservedZoneId =
                            zoneId,
                        timezoneWarning =
                            null,
                    )

                TimezoneObservation.Initialized
            }

            previous == zoneId -> {
                TimezoneObservation.Unchanged
            }

            else -> {
                val warning =
                    TimezoneWarning(
                        previousZoneId =
                            previous,
                        currentZoneId =
                            zoneId,
                    )

                mutableState.value =
                    current.copy(
                        lastObservedZoneId =
                            zoneId,
                        timezoneWarning =
                            warning,
                    )

                TimezoneObservation.Changed(
                    warning =
                        warning,
                )
            }
        }
    }

    override suspend fun dismissTimezoneWarning() {
        mutableState.update {
            it.copy(
                timezoneWarning =
                    null,
            )
        }
    }
}

class InMemoryUserExperiencePreferenceStore(
    initialState: UserExperiencePreferenceState =
        UserExperiencePreferenceState(),
) : UserExperiencePreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<UserExperiencePreferenceState> =
        mutableState

    override suspend fun setFirstDayOfWeekPreference(
        preference: FirstDayOfWeekPreference,
    ) {
        mutableState.update {
            it.copy(
                firstDayOfWeekPreference =
                    preference,
            )
        }
    }

    override suspend fun setSeniorMode(
        seniorMode: SeniorMode,
    ) {
        mutableState.update {
            it.copy(
                seniorMode =
                    seniorMode,
            )
        }
    }
}

class FakeReminderCoordinator(
    var status: ReminderStatus =
        defaultStatus(),
    var remindLaterOutcome:
    RemindLaterOutcome =
        RemindLaterOutcome.SchedulingFailed,
) : ReminderCoordinator {

    val reconcileReasons =
        mutableListOf<ReconciliationReason>()

    val alarmFiredOccurrenceIds =
        mutableListOf<String>()

    val remindLaterOccurrenceIds =
        mutableListOf<String>()

    val cancelledDelayOccurrenceIds =
        mutableListOf<String>()

    var cancelAllOwnedReminderStateCallCount =
        0

    override suspend fun currentStatus():
            ReminderStatus {
        return status
    }

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        reconcileReasons += reason

        return ReminderReconciliationResult.Reconciled(
            reason = reason,
            status = status,
            scheduledCount = 0,
            cancelledCount = 0,
        )
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        alarmFiredOccurrenceIds +=
            occurrenceId

        return AlarmFireResult.NotificationPosted(
            occurrenceId =
                occurrenceId,
            reconciliation =
                ReminderReconciliationResult.Reconciled(
                    reason =
                        ReconciliationReason.ALARM_FIRED,
                    status = status,
                    scheduledCount = 0,
                    cancelledCount = 0,
                ),
        )
    }

    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome {
        remindLaterOccurrenceIds +=
            occurrenceId

        return remindLaterOutcome
    }

    override suspend fun cancelReminderDelay(
        occurrenceId: String,
    ) {
        cancelledDelayOccurrenceIds +=
            occurrenceId
    }

    override suspend fun cancelAllOwnedReminderState() {
        cancelAllOwnedReminderStateCallCount +=
            1
    }

    companion object {
        fun defaultStatus(): ReminderStatus =
            ReminderStatus(
                remindersEnabled = false,
                notificationPermissionGranted = true,
                hasActiveSchedule = false,
                exactAlarmCapabilityGranted = false,
                availability =
                    ReminderAvailability.DISABLED,
            )
    }
}
