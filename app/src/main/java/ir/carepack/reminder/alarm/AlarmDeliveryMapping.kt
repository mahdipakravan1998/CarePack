package ir.carepack.reminder.alarm

import ir.carepack.domain.reminder.ReminderDeliveryMode

internal fun AlarmDeliveryMode.toReminderDeliveryMode(): ReminderDeliveryMode = when (this) {
        AlarmDeliveryMode.EXACT -> ReminderDeliveryMode.EXACT
        AlarmDeliveryMode.APPROXIMATE -> ReminderDeliveryMode.APPROXIMATE
    }
