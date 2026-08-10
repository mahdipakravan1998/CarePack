package ir.carepack.domain.reminder

import java.security.MessageDigest
import java.time.Clock

enum class ReminderDiagnosticEventType {
    FUTURE_OCCURRENCE_CHECKED,
    REMINDERS_DISABLED,
    NOTIFICATION_PERMISSION_CHECKED,
    EXACT_ALARM_CAPABILITY_CHECKED,
    EXACT_ALARM_UNAVAILABLE,
    APPROXIMATE_FALLBACK_SELECTED,
    ALARM_REGISTRATION_ATTEMPTED,
    ALARM_REGISTERED,
    ALARM_REGISTRATION_FAILED,
    RECEIVER_FIRED,
    NOTIFICATION_POST_ATTEMPTED,
    NOTIFICATION_POSTED,
    NOTIFICATION_SKIPPED,
    NOTIFICATION_FAILED,
    SNOOZE_SCHEDULED,
    USER_ACTION_HANDLED,
}

data class ReminderDiagnosticEvent(
    val type: ReminderDiagnosticEventType,
    val occurredAtEpochMillis: Long,
    val occurrenceToken: String? = null,
    val alarmKeyToken: String? = null,
    val availability: ReminderAvailability? = null,
    val deliveryMode: ReminderDeliveryMode? = null,
    val outcome: String? = null,
) {
    init {
        require(occurredAtEpochMillis >= 0L)

        occurrenceToken?.let { token ->
            require(token.isNotBlank())
            require(!token.contains(" "))
            require(token.length <= MAX_TOKEN_LENGTH)
        }

        alarmKeyToken?.let { token ->
            require(token.isNotBlank())
            require(!token.contains(" "))
            require(token.length <= MAX_TOKEN_LENGTH)
        }

        outcome?.let { value ->
            require(value.length <= MAX_OUTCOME_LENGTH)
            require(!value.contains('\n'))
            require(!value.contains('\r'))
        }
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 16

        const val MAX_OUTCOME_LENGTH = 80
    }
}

fun interface ReminderDiagnosticSink {

    fun record(
        event: ReminderDiagnosticEvent,
    )
}

object NoOpReminderDiagnosticSink : ReminderDiagnosticSink {

    override fun record(
        event: ReminderDiagnosticEvent,
    ) {
        Unit
    }
}

object ReminderDiagnosticTokens {

    fun forIdentifier(
        rawValue: String?,
    ): String? {
        val normalized = rawValue
                ?.trim()?.takeIf(
                    String::isNotEmpty,
                ) ?: return null

        return sha256Token(
            value = normalized,
        )
    }

    fun forAlarmKey(
        alarmKey: AlarmKey?,
    ): String? = alarmKey
            ?.stableToken?.take(TOKEN_LENGTH)

    private fun sha256Token(
        value: String,
    ): String {
        val digest = MessageDigest
                .getInstance("SHA-256").digest(
                    value.toByteArray(
                        Charsets.UTF_8,
                    ),
                )

        return digest.joinToString(
                separator = "",
            ) { byte ->
                "%02x".format(
                    byte.toInt() and 0xff,
                )
            }.take(TOKEN_LENGTH)
    }

    private const val TOKEN_LENGTH = 12
}

fun ReminderDiagnosticSink.recordReminderDiagnostic(
    type: ReminderDiagnosticEventType,
    clock: Clock,
    occurrenceId: String? = null,
    alarmKey: AlarmKey? = null,
    availability: ReminderAvailability? = null,
    deliveryMode: ReminderDeliveryMode? = null,
    outcome: String? = null,
) {
    record(
        ReminderDiagnosticEvent(
            type = type,
            occurredAtEpochMillis = clock
                    .instant().toEpochMilli(),
            occurrenceToken = ReminderDiagnosticTokens
                    .forIdentifier(
                        occurrenceId,
                    ),
            alarmKeyToken = ReminderDiagnosticTokens
                    .forAlarmKey(
                        alarmKey,
                    ),
            availability = availability,
            deliveryMode = deliveryMode,
            outcome = outcome
                    ?.trim()?.replace(
                        oldChar = '\n',
                        newChar = ' ',
                    )?.replace(
                        oldChar = '\r',
                        newChar = ' ',
                    )?.take(MAX_SAFE_OUTCOME_LENGTH)
                    ?.takeIf(
                        String::isNotEmpty,
                    ),
        ),
    )
}

private const val MAX_SAFE_OUTCOME_LENGTH = 80
