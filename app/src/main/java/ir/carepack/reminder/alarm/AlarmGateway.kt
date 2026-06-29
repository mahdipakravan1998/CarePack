package ir.carepack.reminder.alarm

import ir.carepack.domain.reminder.AlarmKey
import java.time.Instant

enum class AlarmDeliveryMode {
    EXACT,
    APPROXIMATE,
}

data class AlarmRequest(
    val alarmKey: AlarmKey,
    val occurrenceId: String,
    val triggerAt: Instant,
    val deliveryMode:
    AlarmDeliveryMode,
) {
    init {
        require(occurrenceId.isNotBlank())
    }
}

interface AlarmGateway {

    fun schedule(
        request: AlarmRequest,
    )

    fun cancel(
        alarmKey: AlarmKey,
    )

    fun cancelAll(
        alarmKeys: Set<AlarmKey>,
    ) {
        alarmKeys.forEach(
            ::cancel,
        )
    }
}
