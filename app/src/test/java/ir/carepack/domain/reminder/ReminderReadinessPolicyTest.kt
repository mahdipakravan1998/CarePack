package ir.carepack.domain.reminder

import ir.carepack.reminder.permission.BatteryOptimizationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReadinessPolicyTest {

    @Test
    fun disabledReminders_areNotReady() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = false,
                hasActiveSchedule = true,
                notificationRuntimePermissionRequired = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.IGNORED,
                manufacturer = "Xiaomi",
            )

        assertEquals(
            ReminderReadinessStatus
                .REMINDERS_DISABLED,
            readiness.status,
        )

        assertFalse(
            readiness.canAttemptReminderDelivery,
        )

        assertFalse(
            readiness.usesExactAlarm,
        )

        assertEquals(
            ExactAlarmReadiness.NOT_APPLICABLE,
            readiness.exactAlarm,
        )
    }

    @Test
    fun missingSchedule_isNotReady() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = false,
                notificationRuntimePermissionRequired = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.IGNORED,
                manufacturer = "Samsung",
            )

        assertEquals(
            ReminderReadinessStatus
                .NO_ACTIVE_SCHEDULE,
            readiness.status,
        )

        assertFalse(
            readiness.canAttemptReminderDelivery,
        )

        assertFalse(
            readiness.hasActiveFutureOccurrence,
        )
    }

    @Test
    fun notificationDenied_blocksReminderDelivery() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationRuntimePermissionRequired = true,
                notificationPermissionGranted = false,
                canScheduleExactAlarms = true,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.IGNORED,
                manufacturer = "Google",
            )

        assertEquals(
            ReminderReadinessStatus
                .NOTIFICATION_PERMISSION_REQUIRED,
            readiness.status,
        )

        assertEquals(
            NotificationPermissionReadiness.DENIED,
            readiness.notificationPermission,
        )

        assertFalse(
            readiness.canAttemptReminderDelivery,
        )

        assertFalse(
            readiness.approximateFallbackAvailable,
        )
    }

    @Test
    fun notificationPermissionCanBeNotRequired() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationRuntimePermissionRequired = false,
                notificationPermissionGranted = false,
                canScheduleExactAlarms = true,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.IGNORED,
                manufacturer = "Google",
            )

        assertEquals(
            NotificationPermissionReadiness.NOT_REQUIRED,
            readiness.notificationPermission,
        )

        assertTrue(
            readiness.canAttemptReminderDelivery,
        )
    }

    @Test
    fun exactUnavailable_allowsApproximateFallbackExplanation() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationRuntimePermissionRequired = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = false,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.IGNORED,
                manufacturer = "Google",
            )

        assertEquals(
            ReminderReadinessStatus
                .APPROXIMATE_DELIVERY,
            readiness.status,
        )

        assertEquals(
            ExactAlarmReadiness.UNAVAILABLE,
            readiness.exactAlarm,
        )

        assertTrue(
            readiness.approximateFallbackAvailable,
        )

        assertFalse(
            readiness.usesExactAlarm,
        )

        assertTrue(
            readiness.message.contains(
                "تقریبی",
            ),
        )
    }

    @Test
    fun batteryOptimizationNotIgnored_producesGuidance() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationRuntimePermissionRequired = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.NOT_IGNORED,
                manufacturer = "Google",
            )

        assertEquals(
            ReminderReadinessStatus
                .BATTERY_GUIDANCE_RECOMMENDED,
            readiness.status,
        )

        assertEquals(
            BatteryOptimizationState.NOT_IGNORED,
            readiness.batteryOptimizationState,
        )

        assertTrue(
            readiness.manufacturerGuidanceNeeded,
        )

        assertTrue(
            readiness.usesExactAlarm,
        )
    }

    @Test
    fun xiaomiManufacturerMapsToXiaomiGuidanceRegardlessOfCasing() {
        listOf(
            "xiaomi",
            "Xiaomi",
            "XIAOMI",
            "  Xiaomi  ",
        ).forEach { manufacturer ->
            val guidance =
                ManufacturerGuidanceClassifier
                    .classify(
                        manufacturer =
                            manufacturer,
                    )

            assertEquals(
                ManufacturerGuidanceType.XIAOMI,
                guidance.type,
            )

            assertTrue(
                guidance.body.contains(
                    "MIUI",
                ) ||
                        guidance.body.contains(
                            "HyperOS",
                        ),
            )

            assertTrue(
                guidance
                    .actionItems
                    .joinToString()
                    .contains(
                        "Autostart",
                    ),
            )
        }
    }

    @Test
    fun samsungManufacturerMapsToSamsungGuidanceRegardlessOfCasing() {
        listOf(
            "samsung",
            "Samsung",
            "SAMSUNG",
            "  SAMSUNG  ",
        ).forEach { manufacturer ->
            val guidance =
                ManufacturerGuidanceClassifier
                    .classify(
                        manufacturer =
                            manufacturer,
                    )

            assertEquals(
                ManufacturerGuidanceType.SAMSUNG,
                guidance.type,
            )

            val fullText =
                guidance.body +
                        " " +
                        guidance.actionItems.joinToString()

            assertTrue(
                fullText.contains(
                    "Sleeping",
                ),
            )

            assertTrue(
                fullText.contains(
                    "Notification",
                ),
            )
        }
    }

    @Test
    fun unknownManufacturerMapsToGenericGuidance() {
        val guidance =
            ManufacturerGuidanceClassifier
                .classify(
                    manufacturer =
                        "Unknown Maker",
                )

        assertEquals(
            ManufacturerGuidanceType.GENERIC,
            guidance.type,
        )

        val fullText =
            guidance.body +
                    " " +
                    guidance.actionItems.joinToString()

        assertTrue(
            fullText.contains(
                "Battery",
            ),
        )

        assertTrue(
            fullText.contains(
                "Notification",
            ),
        )
    }

    @Test
    fun xiaomiActiveReminderProducesOemGuidanceStatus() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationRuntimePermissionRequired = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
                exactAlarmRelevant = true,
                batteryOptimizationState =
                    BatteryOptimizationState.IGNORED,
                manufacturer = "Xiaomi",
            )

        assertEquals(
            ReminderReadinessStatus
                .OEM_GUIDANCE_RECOMMENDED,
            readiness.status,
        )

        assertTrue(
            readiness.manufacturerGuidanceNeeded,
        )

        assertEquals(
            ManufacturerGuidanceType.XIAOMI,
            readiness.manufacturerGuidance.type,
        )
    }

    @Test
    fun userFacingGuidanceDoesNotClaimGuaranteedDelivery() {
        val guidanceTexts =
            listOf(
                ManufacturerGuidanceClassifier
                    .classify("Xiaomi"),
                ManufacturerGuidanceClassifier
                    .classify("Samsung"),
                ManufacturerGuidanceClassifier
                    .classify("Other"),
            ).flatMap { guidance ->
                listOf(
                    guidance.title,
                    guidance.body,
                ) + guidance.actionItems
            }

        guidanceTexts.forEach { text ->
            assertFalse(
                text.contains(
                    "تضمین",
                ),
            )

            assertFalse(
                text.contains(
                    "guarantee",
                    ignoreCase = true,
                ),
            )
        }
    }
}
