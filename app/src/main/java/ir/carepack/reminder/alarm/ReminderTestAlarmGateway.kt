package ir.carepack.reminder.alarm

import java.time.Instant

data class ReminderTestAlarmRequest(
    val triggerAt: Instant,
    val deliveryMode: AlarmDeliveryMode,
)

interface ReminderTestAlarmGateway {

    fun scheduleTest(
        request: ReminderTestAlarmRequest,
    )

    fun cancelTest()
}
