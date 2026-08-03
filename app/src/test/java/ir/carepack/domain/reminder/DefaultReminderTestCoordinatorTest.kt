package ir.carepack.domain.reminder

import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.testing.MutableExactAlarmCapabilityGateway
import ir.carepack.testing.MutableNotificationPermissionGateway
import ir.carepack.testing.RecordingReminderTestAlarmGateway
import ir.carepack.testing.RecordingReminderTestNotificationGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultReminderTestCoordinatorTest {

    @Test
    fun scheduleTestReminder_usesExactAlarmThirtySecondsAhead() =
        runTest {
            val alarmGateway =
                RecordingReminderTestAlarmGateway()

            val notificationGateway =
                RecordingReminderTestNotificationGateway()

            val coordinator =
                coordinator(
                    alarmGateway = alarmGateway,
                    notificationGateway =
                        notificationGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                )

            val result =
                coordinator.scheduleTestReminder()

            assertEquals(
                ReminderTestScheduleResult.Scheduled(
                    triggerAt =
                        NOW.plusSeconds(30),
                    deliveryMode =
                        ReminderDeliveryMode.EXACT,
                ),
                result,
            )

            assertEquals(
                listOf(
                    ir.carepack.reminder.alarm
                        .ReminderTestAlarmRequest(
                            triggerAt =
                                NOW.plusSeconds(30),
                            deliveryMode =
                                AlarmDeliveryMode.EXACT,
                        ),
                ),
                alarmGateway.requests,
            )

            assertEquals(1, alarmGateway.cancelCount)
            assertEquals(
                1,
                notificationGateway.cancelCount,
            )
        }

    @Test
    fun scheduleTestReminder_withoutExactCapabilityUsesApproximateAlarm() =
        runTest {
            val alarmGateway =
                RecordingReminderTestAlarmGateway()

            val coordinator =
                coordinator(
                    alarmGateway = alarmGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = false,
                )

            val result =
                coordinator.scheduleTestReminder()

            assertEquals(
                ReminderTestScheduleResult.Scheduled(
                    triggerAt =
                        NOW.plusSeconds(30),
                    deliveryMode =
                        ReminderDeliveryMode.APPROXIMATE,
                ),
                result,
            )

            assertEquals(
                listOf(
                    AlarmDeliveryMode.APPROXIMATE,
                ),
                alarmGateway.requests.map {
                    it.deliveryMode
                },
            )
        }

    @Test
    fun scheduleTestReminder_exactFailureFallsBackToApproximate() =
        runTest {
            val alarmGateway =
                RecordingReminderTestAlarmGateway().apply {
                    failExact = true
                }

            val coordinator =
                coordinator(
                    alarmGateway = alarmGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                )

            val result =
                coordinator.scheduleTestReminder()

            assertEquals(
                ReminderTestScheduleResult.Scheduled(
                    triggerAt =
                        NOW.plusSeconds(30),
                    deliveryMode =
                        ReminderDeliveryMode.APPROXIMATE,
                ),
                result,
            )

            assertEquals(
                listOf(
                    AlarmDeliveryMode.APPROXIMATE,
                ),
                alarmGateway.requests.map {
                    it.deliveryMode
                },
            )
        }

    @Test
    fun scheduleTestReminder_repeatedUseCancelsExistingTestBeforeReplacement() =
        runTest {
            val alarmGateway =
                RecordingReminderTestAlarmGateway()

            val notificationGateway =
                RecordingReminderTestNotificationGateway()

            val coordinator =
                coordinator(
                    alarmGateway = alarmGateway,
                    notificationGateway =
                        notificationGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                )

            coordinator.scheduleTestReminder()
            coordinator.scheduleTestReminder()

            assertEquals(2, alarmGateway.cancelCount)
            assertEquals(
                2,
                notificationGateway.cancelCount,
            )
            assertEquals(2, alarmGateway.requests.size)
            assertTrue(
                alarmGateway.requests.all {
                    it.triggerAt == NOW.plusSeconds(30)
                },
            )
        }

    @Test
    fun scheduleTestReminder_permissionUnavailableDoesNotScheduleAlarm() =
        runTest {
            val alarmGateway =
                RecordingReminderTestAlarmGateway()

            val coordinator =
                coordinator(
                    alarmGateway = alarmGateway,
                    permissionGranted = false,
                    exactCapabilityGranted = true,
                )

            val result =
                coordinator.scheduleTestReminder()

            assertEquals(
                ReminderTestScheduleResult
                    .NotificationPermissionRequired,
                result,
            )
            assertTrue(alarmGateway.requests.isEmpty())
            assertEquals(1, alarmGateway.cancelCount)
        }

    @Test
    fun scheduleTestReminder_whenBothModesFailReportsSchedulingUnavailable() =
        runTest {
            val alarmGateway =
                RecordingReminderTestAlarmGateway().apply {
                    failExact = true
                    failApproximate = true
                }

            val coordinator =
                coordinator(
                    alarmGateway = alarmGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                )

            assertEquals(
                ReminderTestScheduleResult
                    .SchedulingUnavailable,
                coordinator.scheduleTestReminder(),
            )
            assertTrue(alarmGateway.requests.isEmpty())
        }

    @Test
    fun handleTestAlarmFired_postsOnlyDedicatedTestNotification() =
        runTest {
            val notificationGateway =
                RecordingReminderTestNotificationGateway()

            val coordinator =
                coordinator(
                    notificationGateway =
                        notificationGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                )

            assertEquals(
                ReminderTestFireResult
                    .NotificationPosted,
                coordinator.handleTestAlarmFired(),
            )
            assertEquals(
                listOf(NOW),
                notificationGateway.postedAt,
            )
        }

    @Test
    fun handleTestAlarmFired_permissionUnavailableDoesNotPostNotification() =
        runTest {
            val notificationGateway =
                RecordingReminderTestNotificationGateway()

            val coordinator =
                coordinator(
                    notificationGateway =
                        notificationGateway,
                    permissionGranted = false,
                    exactCapabilityGranted = true,
                )

            assertEquals(
                ReminderTestFireResult
                    .NotificationPermissionUnavailable,
                coordinator.handleTestAlarmFired(),
            )
            assertTrue(
                notificationGateway.postedAt.isEmpty(),
            )
        }

    @Test
    fun handleTestAlarmFired_notificationFailureIsRepresentedHonestly() =
        runTest {
            val notificationGateway =
                RecordingReminderTestNotificationGateway().apply {
                    failPost = true
                }

            val coordinator =
                coordinator(
                    notificationGateway =
                        notificationGateway,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                )

            assertEquals(
                ReminderTestFireResult
                    .NotificationFailed,
                coordinator.handleTestAlarmFired(),
            )
        }

    private fun coordinator(
        alarmGateway:
        RecordingReminderTestAlarmGateway =
            RecordingReminderTestAlarmGateway(),
        notificationGateway:
        RecordingReminderTestNotificationGateway =
            RecordingReminderTestNotificationGateway(),
        permissionGranted: Boolean,
        exactCapabilityGranted: Boolean,
    ): DefaultReminderTestCoordinator =
        DefaultReminderTestCoordinator(
            notificationPermissionGateway =
                MutableNotificationPermissionGateway(
                    permissionGranted =
                        permissionGranted,
                ),
            exactAlarmCapabilityGateway =
                MutableExactAlarmCapabilityGateway(
                    exactCapabilityGranted =
                        exactCapabilityGranted,
                ),
            alarmGateway = alarmGateway,
            notificationGateway =
                notificationGateway,
            clock =
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC,
                ),
            operationLock =
                ReminderOperationLock(),
        )

    private companion object {
        val NOW: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )
    }
}
