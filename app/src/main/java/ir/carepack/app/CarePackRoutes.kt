package ir.carepack.app

internal object CarePackRoutes {
    const val Onboarding = "onboarding"
    const val Recipient = "recipient"
    const val Today = "today"
    const val CarePlan = "care-plan"
    const val Calendar = "calendar"
    const val Settings = "settings"
    const val ReminderSettings = "reminder-settings"
    const val TodayReport = "today-report"
    const val RangeReport = "range-report"
    const val Privacy = "privacy"
    const val DeleteAllData = "delete-all-data"
    const val EditRecipientName = "edit-recipient-name"

    const val RecipientIdArgument = "recipientId"
    const val MedicationIdArgument = "medicationId"
    const val ScheduleSeriesIdArgument = "scheduleSeriesId"
    const val OccurrenceIdArgument = "occurrenceId"

    const val MedicationSchedulePattern = "medication-schedule/{$RecipientIdArgument}"
    const val AddMedicationPattern = "add-medication/{$RecipientIdArgument}"
    const val AddSchedulePattern = "add-schedule/{$MedicationIdArgument}"
    const val EditMedicationTextPattern = "edit-medication/{$MedicationIdArgument}"
    const val EditSchedulePattern = "edit-schedule/{$ScheduleSeriesIdArgument}"
    const val DeleteMedicationPattern = "delete-medication/{$MedicationIdArgument}"
    const val OccurrenceDetailPattern = "occurrence/{$OccurrenceIdArgument}"
    const val ReminderOccurrenceDetailPattern = "reminder/occurrence/{$OccurrenceIdArgument}"

    fun medicationSchedule(recipientId: String): String = "medication-schedule/$recipientId"

    fun addMedication(recipientId: String): String = "add-medication/$recipientId"

    fun addSchedule(medicationId: String): String = "add-schedule/$medicationId"

    fun editMedicationText(medicationId: String): String = "edit-medication/$medicationId"

    fun editSchedule(scheduleSeriesId: String): String = "edit-schedule/$scheduleSeriesId"

    fun deleteMedication(medicationId: String): String = "delete-medication/$medicationId"

    fun occurrenceDetail(occurrenceId: String): String = "occurrence/$occurrenceId"

    fun reminderOccurrenceDetail(occurrenceId: String): String = "reminder/occurrence/$occurrenceId"
}
