package ir.carepack.domain.reminder

import ir.carepack.reminder.permission.BatteryOptimizationState

enum class ReminderReadinessStatus {
    READY,
    REMINDERS_DISABLED,
    NO_ACTIVE_SCHEDULE,
    NOTIFICATION_PERMISSION_REQUIRED,
    EXACT_ALARM_ACCESS_RECOMMENDED,
    APPROXIMATE_DELIVERY,
    BATTERY_GUIDANCE_RECOMMENDED,
    OEM_GUIDANCE_RECOMMENDED,
}

enum class NotificationPermissionReadiness {
    GRANTED,
    DENIED,
    NOT_REQUIRED,
}

enum class ExactAlarmReadiness {
    AVAILABLE,
    UNAVAILABLE,
    NOT_APPLICABLE,
}

enum class ManufacturerGuidanceType {
    XIAOMI,
    SAMSUNG,
    GENERIC,
}

data class ManufacturerGuidance(
    val type: ManufacturerGuidanceType,
    val title: String,
    val body: String,
    val actionItems: List<String>,
) {
    init {
        require(title.isNotBlank())
        require(body.isNotBlank())
        require(actionItems.isNotEmpty())
    }
}

object ManufacturerGuidanceClassifier {

    fun classify(
        manufacturer: String?,
    ): ManufacturerGuidance {
        val normalized = manufacturer
                ?.trim()?.lowercase()
                .orEmpty()

        return when {
            normalized.contains(
                other = "xiaomi",
            ) || normalized.contains(
                        other = "redmi",
                    ) || normalized.contains(
                        other = "poco",
                    ) -> {
                ManufacturerGuidance(
                    type = ManufacturerGuidanceType.XIAOMI,
                    title = "راهنمای گوشی شیائومی",
                    body =
                        "در MIUI یا HyperOS ممکن است Autostart، محدودیت باتری، اجرای پس‌زمینه یا نمایش پنجره در پس‌زمینه باعث تأخیر یا نمایش‌ندادن یادآور شود.",
                    actionItems = listOf(
                            "Autostart را برای کرپک بررسی کنید.",
                            "Battery restrictions را برای کرپک روی حالت آزادتر بگذارید.",
                            "Background activity و نمایش پنجره یا اعلان در پس‌زمینه را در صورت وجود بررسی کنید.",
                            "کرپک نمی‌تواند این تنظیمات محافظت‌شده را خودش تغییر دهد.",
                        ),
                )
            }

            normalized.contains(
                other = "samsung",
            ) -> {
                ManufacturerGuidance(
                    type = ManufacturerGuidanceType.SAMSUNG,
                    title = "راهنمای گوشی سامسونگ",
                    body =
                        "در One UI ممکن است Sleeping apps، Deep sleeping apps، Battery optimization یا تنظیمات دسته اعلان‌ها یادآورها را محدود یا با تأخیر نمایش دهد.",
                    actionItems = listOf(
                            "Sleeping apps و Deep sleeping apps را بررسی کنید.",
                            "Battery optimization را برای کرپک بررسی کنید.",
                            "Notification categories/settings کرپک را بررسی کنید.",
                            "کرپک نمی‌تواند این تنظیمات محافظت‌شده را خودش تغییر دهد.",
                        ),
                )
            }

            else -> {
                ManufacturerGuidance(
                    type = ManufacturerGuidanceType.GENERIC,
                    title = "راهنمای محدودیت‌های گوشی",
                    body =
                        "حالت کم‌مصرف، بهینه‌سازی باتری، تنظیمات اعلان و محدودیت‌های سازنده ممکن است یادآورها را با تأخیر نمایش دهند یا محدود کنند.",
                    actionItems = listOf(
                            "Battery Saver را بررسی کنید.",
                            "Battery optimization کرپک را بررسی کنید.",
                            "Notification settings کرپک را بررسی کنید.",
                            "ممکن است سازنده گوشی محدودیت‌های اضافه داشته باشد.",
                        ),
                )
            }
        }
    }
}

data class ReminderReadiness(
    val status: ReminderReadinessStatus,
    val canAttemptReminderDelivery: Boolean,
    val usesExactAlarm: Boolean,
    val notificationPermission: NotificationPermissionReadiness,
    val exactAlarm: ExactAlarmReadiness,
    val approximateFallbackAvailable: Boolean,
    val remindersEnabled: Boolean,
    val hasActiveFutureOccurrence: Boolean,
    val batteryOptimizationState: BatteryOptimizationState,
    val manufacturerGuidance: ManufacturerGuidance,
    val manufacturerGuidanceNeeded: Boolean,
    val message: String,
) {
    val hasReadinessProblem: Boolean
        get() = status != ReminderReadinessStatus.READY
}

object ReminderReadinessPolicy {

    fun evaluate(
        remindersEnabled: Boolean,
        hasActiveSchedule: Boolean,
        notificationPermissionGranted: Boolean,
        canScheduleExactAlarms: Boolean,
    ): ReminderReadiness = evaluate(
            remindersEnabled = remindersEnabled,
            hasActiveSchedule = hasActiveSchedule,
            notificationRuntimePermissionRequired = true,
            notificationPermissionGranted = notificationPermissionGranted,
            canScheduleExactAlarms = canScheduleExactAlarms,
            exactAlarmRelevant = true,
            batteryOptimizationState = BatteryOptimizationState.UNKNOWN,
            manufacturer = null,
        )

    fun evaluate(
        remindersEnabled: Boolean,
        hasActiveSchedule: Boolean,
        notificationRuntimePermissionRequired: Boolean,
        notificationPermissionGranted: Boolean,
        canScheduleExactAlarms: Boolean,
        exactAlarmRelevant: Boolean,
        batteryOptimizationState: BatteryOptimizationState,
        manufacturer: String?,
    ): ReminderReadiness {
        val notificationReadiness = when {
                !notificationRuntimePermissionRequired -> {
                    NotificationPermissionReadiness.NOT_REQUIRED
                }

                notificationPermissionGranted -> {
                    NotificationPermissionReadiness.GRANTED
                }

                else -> {
                    NotificationPermissionReadiness.DENIED
                }
            }

        val exactReadiness = when {
                !exactAlarmRelevant || !remindersEnabled ||
                        !hasActiveSchedule || notificationReadiness ==
                        NotificationPermissionReadiness.DENIED -> {
                    ExactAlarmReadiness.NOT_APPLICABLE
                }

                canScheduleExactAlarms -> {
                    ExactAlarmReadiness.AVAILABLE
                }

                else -> {
                    ExactAlarmReadiness.UNAVAILABLE
                }
            }

        val guidance = ManufacturerGuidanceClassifier
                .classify(
                    manufacturer = manufacturer,
                )

        val activeAndDeliverable = remindersEnabled &&
                    hasActiveSchedule && notificationReadiness !=
                    NotificationPermissionReadiness.DENIED

        val manufacturerGuidanceNeeded = activeAndDeliverable &&
                    guidance.type != ManufacturerGuidanceType.GENERIC

        val status = when {
                !remindersEnabled -> {
                    ReminderReadinessStatus.REMINDERS_DISABLED
                }

                !hasActiveSchedule -> {
                    ReminderReadinessStatus.NO_ACTIVE_SCHEDULE
                }

                notificationReadiness == NotificationPermissionReadiness
                            .DENIED -> {
                    ReminderReadinessStatus.NOTIFICATION_PERMISSION_REQUIRED
                }

                exactReadiness == ExactAlarmReadiness
                            .UNAVAILABLE -> {
                    ReminderReadinessStatus.APPROXIMATE_DELIVERY
                }

                batteryOptimizationState == BatteryOptimizationState
                            .NOT_IGNORED -> {
                    ReminderReadinessStatus.BATTERY_GUIDANCE_RECOMMENDED
                }

                manufacturerGuidanceNeeded -> {
                    ReminderReadinessStatus.OEM_GUIDANCE_RECOMMENDED
                }

                else -> {
                    ReminderReadinessStatus.READY
                }
            }

        return ReminderReadiness(
            status = status,
            canAttemptReminderDelivery = activeAndDeliverable,
            usesExactAlarm = exactReadiness ==
                        ExactAlarmReadiness.AVAILABLE,
            notificationPermission = notificationReadiness,
            exactAlarm = exactReadiness,
            approximateFallbackAvailable = activeAndDeliverable &&
                        exactReadiness == ExactAlarmReadiness
                            .UNAVAILABLE,
            remindersEnabled = remindersEnabled,
            hasActiveFutureOccurrence = hasActiveSchedule,
            batteryOptimizationState = batteryOptimizationState,
            manufacturerGuidance = guidance,
            manufacturerGuidanceNeeded = manufacturerGuidanceNeeded ||
                        (
                                activeAndDeliverable && batteryOptimizationState ==
                                        BatteryOptimizationState.NOT_IGNORED
                                ),
            message = messageFor(
                    status = status,
                ),
        )
    }

    private fun messageFor(
        status: ReminderReadinessStatus,
    ): String = when (status) {
            ReminderReadinessStatus.READY -> {
                "وضعیت فعلی برای تلاش جهت نمایش یادآور آماده است، اما اندروید یا تنظیمات باتری گوشی همچنان ممکن است آن را با تأخیر نمایش دهد یا محدود کند."
            }

            ReminderReadinessStatus.REMINDERS_DISABLED -> {
                "یادآورها خاموش هستند."
            }

            ReminderReadinessStatus.NO_ACTIVE_SCHEDULE -> {
                "برای فعال شدن یادآورها ابتدا یک برنامه دارویی فعال لازم است."
            }

            ReminderReadinessStatus.NOTIFICATION_PERMISSION_REQUIRED -> {
                "برای نمایش یادآور دارو، اجازه اعلان لازم است. بدون این اجازه ثبت و مدیریت نوبت‌ها همچنان قابل استفاده است."
            }

            ReminderReadinessStatus.EXACT_ALARM_ACCESS_RECOMMENDED -> {
                "دسترسی یادآور دقیق‌تر پیشنهاد می‌شود. بدون آن، کرپک از زمان‌بندی تقریبی استفاده می‌کند."
            }

            ReminderReadinessStatus.APPROXIMATE_DELIVERY -> {
                "یادآور با زمان‌بندی تقریبی ادامه می‌دهد و ممکن است با تأخیر نمایش داده شود."
            }

            ReminderReadinessStatus.BATTERY_GUIDANCE_RECOMMENDED -> {
                "برای کاهش تأخیر، تنظیمات باتری و اجرای پس‌زمینه کرپک را بررسی کنید. تحویل یادآور تضمین‌شده نیست."
            }

            ReminderReadinessStatus.OEM_GUIDANCE_RECOMMENDED -> {
                "سازنده گوشی ممکن است یادآورها را محدود کند. راهنمای مخصوص گوشی را بررسی کنید."
            }
        }
}
