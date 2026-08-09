package ir.carepack.testing

import ir.carepack.data.preferences.PrivacyPreferenceState
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderDeliveryMode
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.domain.reminder.ReminderTestFireResult
import ir.carepack.domain.reminder.ReminderTestScheduleResult
import ir.carepack.domain.reminder.SnoozedReminder
import ir.carepack.domain.reminder.SnoozedReminderStore
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeReportContent
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.domain.report.RangeReportText
import ir.carepack.domain.report.RangeSummaryBuilder
import ir.carepack.domain.report.ReportDateRange
import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.alarm.ReminderTestAlarmGateway
import ir.carepack.reminder.alarm.ReminderTestAlarmRequest
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.notification.ReminderTestNotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.settings.deletion.DeletionMarkerCorruptionReason
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionCounts
import ir.carepack.settings.deletion.MedicationDeletionDataSource
import ir.carepack.settings.deletion.MedicationDeletionGraph
import ir.carepack.settings.deletion.MedicationDeletionMarker
import ir.carepack.settings.deletion.MedicationDeletionMarkerReadResult
import ir.carepack.settings.deletion.MedicationDeletionMarkerStage
import ir.carepack.settings.deletion.MedicationDeletionMarkerStore
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionRecoveryResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import ir.carepack.settings.deletion.MedicationGraphDeletionResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MutableNotificationPermissionGateway(
    var permissionGranted: Boolean = true,
    var runtimePermissionRequired: Boolean = true,
) : NotificationPermissionGateway {

    override fun isPermissionGranted(): Boolean =
        permissionGranted

    override fun requiresRuntimePermission(): Boolean =
        runtimePermissionRequired
}

class MutableExactAlarmCapabilityGateway(
    var exactCapabilityGranted: Boolean = true,
) : ExactAlarmCapabilityGateway {

    override fun canScheduleExactAlarms(): Boolean =
        exactCapabilityGranted
}

class RecordingReminderTestAlarmGateway :
    ReminderTestAlarmGateway {

    val requests =
        mutableListOf<ReminderTestAlarmRequest>()

    var cancelCount: Int = 0

    var failExact: Boolean = false

    var failApproximate: Boolean = false

    override fun scheduleTest(
        request: ReminderTestAlarmRequest,
    ) {
        when (request.deliveryMode) {
            AlarmDeliveryMode.EXACT -> {
                if (failExact) {
                    error("Exact test alarm scheduling failed.")
                }
            }

            AlarmDeliveryMode.APPROXIMATE -> {
                if (failApproximate) {
                    error("Approximate test alarm scheduling failed.")
                }
            }
        }

        requests += request
    }

    override fun cancelTest() {
        cancelCount += 1
    }
}

class RecordingReminderTestNotificationGateway :
    ReminderTestNotificationGateway {

    val postedAt =
        mutableListOf<Instant>()

    var cancelCount: Int = 0

    var failPost: Boolean = false

    override fun postTestReminder(
        scheduledAt: Instant,
    ) {
        if (failPost) {
            error("Test notification failed.")
        }

        postedAt += scheduledAt
    }

    override fun cancelTestReminder() {
        cancelCount += 1
    }
}

class QueueReminderTestCoordinator(
    scheduleResults:
    List<ReminderTestScheduleResult> =
        listOf(
            ReminderTestScheduleResult.Scheduled(
                triggerAt =
                    Instant.parse(
                        "2026-06-24T08:00:30Z",
                    ),
                deliveryMode =
                    ReminderDeliveryMode.EXACT,
            ),
        ),
    var fireResult:
    ReminderTestFireResult =
        ReminderTestFireResult.NotificationPosted,
) : ReminderTestCoordinator {

    private val mutableScheduleResults =
        scheduleResults.toMutableList()

    val requestedDelays =
        mutableListOf<Long>()

    var fireCallCount: Int = 0

    var cancelCallCount: Int = 0

    override suspend fun scheduleTestReminder(
        delaySeconds: Long,
    ): ReminderTestScheduleResult {
        requestedDelays += delaySeconds

        if (mutableScheduleResults.isEmpty()) {
            return ReminderTestScheduleResult
                .SchedulingUnavailable
        }

        return mutableScheduleResults.removeAt(0)
    }

    override suspend fun handleTestAlarmFired():
            ReminderTestFireResult {
        fireCallCount += 1
        return fireResult
    }

    override suspend fun cancelPendingTest() {
        cancelCallCount += 1
    }
}

class InMemoryPrivacyPreferenceStore(
    initialState: PrivacyPreferenceState =
        PrivacyPreferenceState(),
) : PrivacyPreferenceStore {

    private val mutableState =
        MutableStateFlow(initialState)

    override val state: Flow<PrivacyPreferenceState> =
        mutableState

    override suspend fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        mutableState.update { current ->
            current.copy(
                includeRecipientName =
                    includeRecipientName,
            )
        }
    }

}

class RecordingTextShareGateway(
    var copyResult: CopyTextResult =
        CopyTextResult.Copied,
    var shareResult: ShareTextResult =
        ShareTextResult.ChooserOpened,
) : TextShareGateway {

    val copiedTexts =
        mutableListOf<String>()

    val sharedTexts =
        mutableListOf<String>()

    override fun share(
        text: String,
        descriptor: ir.carepack.reporting.share.ShareDescriptor,
    ): ShareTextResult {
        sharedTexts += text
        return shareResult
    }

    override fun copy(
        text: String,
        descriptor: ir.carepack.reporting.share.ShareDescriptor,
    ): CopyTextResult {
        copiedTexts += text
        return copyResult
    }
}

class RecordingDateRangeSummaryService(
    initialEntries:
    List<RangeOccurrenceEntry> = emptyList(),
) : DateRangeSummaryService {

    private val entries =
        MutableStateFlow(initialEntries)

    val observedRanges =
        mutableListOf<ReportDateRange>()

    val requestedRanges =
        mutableListOf<ReportDateRange>()

    var observationFailure: Throwable? = null

    var requestFailure: Throwable? = null

    fun setEntries(
        value: List<RangeOccurrenceEntry>,
    ) {
        entries.value = value
    }

    override fun observeSummary(
        range: ReportDateRange,
    ): Flow<DateRangeSummary> {
        observedRanges += range

        observationFailure?.let { failure ->
            return kotlinx.coroutines.flow.flow {
                throw failure
            }
        }

        return entries.map { currentEntries ->
            RangeSummaryBuilder.build(
                range = range,
                entries = currentEntries,
            )
        }
    }

    override suspend fun getSummary(
        range: ReportDateRange,
    ): DateRangeSummary {
        requestedRanges += range
        requestFailure?.let { throw it }

        return RangeSummaryBuilder.build(
            range = range,
            entries = entries.value,
        )
    }
}

class RecordingRangeReportFormatter(
    initialEntries:
    List<RangeOccurrenceEntry> = emptyList(),
) : RangeReportFormatter {

    private var entries =
        initialEntries

    val requests =
        mutableListOf<RangeReportRequest>()

    var failure: Throwable? = null

    fun setEntries(
        value: List<RangeOccurrenceEntry>,
    ) {
        entries = value
    }

    override suspend fun createRangeReport(
        period: RangeReportPeriod,
        today: LocalDate,
        includeRecipientName: Boolean,
    ): RangeReportContent {
        requests +=
            RangeReportRequest(
                period = period,
                today = today,
                includeRecipientName =
                    includeRecipientName,
            )

        failure?.let { throw it }

        val summary =
            RangeSummaryBuilder.build(
                range =
                    period.rangeEndingAt(today),
                entries = entries,
            )

        return RangeReportContent(
            period = period,
            summary = summary,
            text =
                RangeReportText(
                    value =
                        "${period.name}|$today|$includeRecipientName|${summary.totalOccurrenceCount}",
                ),
        )
    }
}

data class RangeReportRequest(
    val period: RangeReportPeriod,
    val today: LocalDate,
    val includeRecipientName: Boolean,
)

class InMemoryMedicationDeletionMarkerStore(
    initialMarker: MedicationDeletionMarker? = null,
    initialCorruption:
        DeletionMarkerCorruptionReason? = null,
) : MedicationDeletionMarkerStore {

    private val mutableState =
        MutableStateFlow<MedicationDeletionMarkerReadResult>(
            when {
                initialCorruption != null ->
                    MedicationDeletionMarkerReadResult.Corrupted(
                        initialCorruption,
                    )
                initialMarker != null ->
                    MedicationDeletionMarkerReadResult.Valid(
                        initialMarker,
                    )
                else ->
                    MedicationDeletionMarkerReadResult.Absent
            },
        )

    override val state:
        Flow<MedicationDeletionMarkerReadResult> =
        mutableState

    val marker: Flow<MedicationDeletionMarker?> =
        state.map { result ->
            (result as? MedicationDeletionMarkerReadResult.Valid)
                ?.marker
        }

    val savedMarkers =
        mutableListOf<MedicationDeletionMarker>()

    val stageUpdates =
        mutableListOf<Pair<String, MedicationDeletionMarkerStage>>()

    val clearedMedicationIds =
        mutableListOf<String>()

    var saveFailure: Throwable? = null
    var updateFailure: Throwable? = null
    var clearFailure: Throwable? = null

    override suspend fun save(
        marker: MedicationDeletionMarker,
    ) {
        saveFailure?.let { throw it }
        savedMarkers += marker
        mutableState.value =
            MedicationDeletionMarkerReadResult.Valid(marker)
    }

    override suspend fun updateStage(
        medicationId: String,
        stage: MedicationDeletionMarkerStage,
    ) {
        updateFailure?.let { throw it }
        stageUpdates += medicationId to stage

        val current =
            (mutableState.value as?
                MedicationDeletionMarkerReadResult.Valid)
                ?.marker

        if (
            current != null &&
            current.expectedPreview.medicationId == medicationId
        ) {
            mutableState.value =
                MedicationDeletionMarkerReadResult.Valid(
                    current.withStage(stage),
                )
        }
    }

    override suspend fun clear(
        medicationId: String,
    ) {
        clearFailure?.let { throw it }
        clearedMedicationIds += medicationId

        val current =
            (mutableState.value as?
                MedicationDeletionMarkerReadResult.Valid)
                ?.marker

        if (current?.expectedPreview?.medicationId == medicationId) {
            mutableState.value =
                MedicationDeletionMarkerReadResult.Absent
        }
    }
}

class FakeMedicationDeletionDataSource(
    var graph: MedicationDeletionGraph? = null,
) : MedicationDeletionDataSource {

    val previewRequests =
        mutableListOf<String>()

    val graphRequests =
        mutableListOf<String>()

    val deletionRequests =
        mutableListOf<Pair<String, MedicationDeletionPreview?>>()

    var previewFailure: Throwable? = null

    var graphFailure: Throwable? = null

    var deleteFailure: Throwable? = null

    var deletionResult:
            MedicationGraphDeletionResult? = null

    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreview? {
        previewRequests += medicationId
        previewFailure?.let { throw it }
        return graph?.preview
    }

    override suspend fun loadGraph(
        medicationId: String,
    ): MedicationDeletionGraph? {
        graphRequests += medicationId
        graphFailure?.let { throw it }
        return graph
    }

    override suspend fun deleteGraph(
        medicationId: String,
        expectedPreview: MedicationDeletionPreview?,
    ): MedicationGraphDeletionResult {
        deletionRequests +=
            medicationId to expectedPreview

        deleteFailure?.let { throw it }

        val configured = deletionResult
        if (configured != null) {
            if (configured is MedicationGraphDeletionResult.Deleted) {
                graph = null
            }
            return configured
        }

        val current = graph
            ?: return MedicationGraphDeletionResult.NotFound

        if (
            expectedPreview != null &&
            expectedPreview != current.preview
        ) {
            return MedicationGraphDeletionResult
                .ChangedSincePreview(
                    latestPreview = current.preview,
                )
        }

        graph = null

        return MedicationGraphDeletionResult.Deleted(
            counts =
                MedicationDeletionCounts(
                    caregiverReportCount =
                        current.preview
                            .caregiverReportCount,
                    occurrenceCount =
                        current.preview
                            .occurrenceCount,
                    scheduleTimeCount =
                        current.preview
                            .scheduleTimeCount,
                    scheduleVersionCount =
                        current.preview
                            .scheduleVersionCount,
                    scheduleSeriesCount =
                        current.preview
                            .scheduleSeriesCount,
                    medicationCount = 1,
                ),
        )
    }
}

class RecordingAlarmGateway : AlarmGateway {

    val scheduledRequests =
        mutableListOf<AlarmRequest>()

    val cancelledKeys =
        mutableListOf<AlarmKey>()

    var scheduleFailure: Throwable? = null

    var cancelFailure: Throwable? = null

    override fun schedule(
        request: AlarmRequest,
    ) {
        scheduleFailure?.let { throw it }
        scheduledRequests += request
    }

    override fun cancel(
        alarmKey: AlarmKey,
    ) {
        cancelFailure?.let { throw it }
        cancelledKeys += alarmKey
    }
}

class RecordingNotificationGateway :
    NotificationGateway {

    val postedNotifications =
        mutableListOf<ReminderNotification>()

    val cancelledOccurrenceIds =
        mutableListOf<String>()

    var cancelAllCallCount: Int = 0

    var postFailure: Throwable? = null

    var cancelFailure: Throwable? = null

    var cancelAllFailure: Throwable? = null

    override fun post(
        notification: ReminderNotification,
    ) {
        postFailure?.let { throw it }
        postedNotifications += notification
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        cancelFailure?.let { throw it }
        cancelledOccurrenceIds += occurrenceId
    }

    override fun cancelAll() {
        cancelAllFailure?.let { throw it }
        cancelAllCallCount += 1
    }
}

class InMemorySnoozedReminderStore(
    initialReminders: List<SnoozedReminder> =
        emptyList(),
) : SnoozedReminderStore {

    private val mutableReminders =
        MutableStateFlow(initialReminders)

    override val reminders:
            Flow<List<SnoozedReminder>> =
        mutableReminders

    val deletedOccurrenceIds =
        mutableListOf<String>()

    override suspend fun upsert(
        reminder: SnoozedReminder,
    ) {
        mutableReminders.update { current ->
            current
                .filterNot {
                    it.occurrenceId ==
                            reminder.occurrenceId
                } + reminder
        }
    }

    override suspend fun delete(
        occurrenceId: String,
    ) {
        deletedOccurrenceIds += occurrenceId
        mutableReminders.update { current ->
            current.filterNot {
                it.occurrenceId == occurrenceId
            }
        }
    }

    override suspend fun clear() {
        mutableReminders.value = emptyList()
    }
}

class RecordingCoreReminderCoordinator(
    var status: ReminderStatus =
        ReminderStatus(
            remindersEnabled = true,
            notificationPermissionGranted = true,
            hasActiveSchedule = true,
            exactAlarmCapabilityGranted = true,
            availability =
                ReminderAvailability.EXACT,
        ),
) : ReminderCoordinator {

    val reconcileReasons =
        mutableListOf<ReconciliationReason>()

    var reconcileAsPartialFailure: Boolean = false

    var reconcileFailure: Throwable? = null

    var cancelAllOwnedCallCount: Int = 0

    override suspend fun currentStatus(): ReminderStatus =
        status

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        reconcileReasons += reason
        reconcileFailure?.let { throw it }

        return if (reconcileAsPartialFailure) {
            ReminderReconciliationResult
                .PartialFailure(
                    reason = reason,
                    status = status,
                    scheduledCount = 0,
                    cancelledCount = 0,
                    failedOperationCount = 1,
                )
        } else {
            ReminderReconciliationResult
                .Reconciled(
                    reason = reason,
                    status = status,
                    scheduledCount = 0,
                    cancelledCount = 0,
                )
        }
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult =
        AlarmFireResult.NotificationPosted(
            occurrenceId = occurrenceId,
            reconciliation =
                ReminderReconciliationResult
                    .Reconciled(
                        reason =
                            ReconciliationReason
                                .ALARM_FIRED,
                        status = status,
                        scheduledCount = 0,
                        cancelledCount = 0,
                    ),
        )

    override suspend fun cancelAllOwnedReminderState() {
        cancelAllOwnedCallCount += 1
    }
}

class QueueMedicationDeletionCoordinator(
    previewResults:
    List<MedicationDeletionPreviewResult>,
    deletionResults:
    List<MedicationDeletionResult> = emptyList(),
    var recoveryResult:
    MedicationDeletionRecoveryResult =
        MedicationDeletionRecoveryResult
            .NoDeletionPending,
) : MedicationDeletionCoordinator {

    private val mutablePreviewResults =
        previewResults.toMutableList()

    private val mutableDeletionResults =
        deletionResults.toMutableList()

    val previewMedicationIds =
        mutableListOf<String>()

    val deletionPreviews =
        mutableListOf<MedicationDeletionPreview>()

    var recoveryCallCount: Int = 0

    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreviewResult {
        previewMedicationIds += medicationId

        if (mutablePreviewResults.isEmpty()) {
            return MedicationDeletionPreviewResult.NotFound
        }

        return mutablePreviewResults.removeAt(0)
    }

    override suspend fun deleteMedication(
        expectedPreview: MedicationDeletionPreview,
    ): MedicationDeletionResult {
        deletionPreviews += expectedPreview

        if (mutableDeletionResults.isEmpty()) {
            return MedicationDeletionResult.AlreadyDeleted
        }

        return mutableDeletionResults.removeAt(0)
    }

    override suspend fun resumeIncompleteDeletionIfNeeded():
            MedicationDeletionRecoveryResult {
        recoveryCallCount += 1
        return recoveryResult
    }
}
