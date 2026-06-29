package ir.carepack.reminder.notification

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.carepack.MainActivity

object ReminderNotificationContract {

    const val CHANNEL_ID =
        "carepack_reminders"

    const val ACTION_OPEN_OCCURRENCE =
        "ir.carepack.action.OPEN_REMINDER_OCCURRENCE"

    const val EXTRA_OCCURRENCE_ID =
        "ir.carepack.extra.OCCURRENCE_ID"

    private const val URI_SCHEME =
        "carepack"

    private const val URI_AUTHORITY =
        "reminder"

    private const val URI_OCCURRENCE_PATH =
        "occurrence"

    fun createOpenOccurrenceIntent(
        context: Context,
        occurrenceId: String,
    ): Intent {
        require(occurrenceId.isNotBlank())

        return Intent(
            context,
            MainActivity::class.java,
        ).apply {
            action =
                ACTION_OPEN_OCCURRENCE

            data =
                createOccurrenceUri(
                    occurrenceId =
                        occurrenceId,
                )

            putExtra(
                EXTRA_OCCURRENCE_ID,
                occurrenceId,
            )

            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun extractOccurrenceId(
        intent: Intent?,
    ): String? {
        if (
            intent?.action !=
            ACTION_OPEN_OCCURRENCE
        ) {
            return null
        }

        val uriOccurrenceId =
            extractOccurrenceId(
                uri = intent.data,
            ) ?: return null

        val extraOccurrenceId =
            intent
                .getStringExtra(
                    EXTRA_OCCURRENCE_ID,
                )
                ?.trim()
                ?.takeIf(
                    String::isNotEmpty,
                )
                ?: return null

        return if (
            uriOccurrenceId ==
            extraOccurrenceId
        ) {
            uriOccurrenceId
        } else {
            null
        }
    }

    private fun createOccurrenceUri(
        occurrenceId: String,
    ): Uri {
        return Uri.Builder()
            .scheme(URI_SCHEME)
            .authority(URI_AUTHORITY)
            .appendPath(
                URI_OCCURRENCE_PATH,
            )
            .appendPath(
                occurrenceId,
            )
            .build()
    }

    private fun extractOccurrenceId(
        uri: Uri?,
    ): String? {
        if (
            uri?.scheme != URI_SCHEME ||
            uri.authority != URI_AUTHORITY
        ) {
            return null
        }

        val pathSegments =
            uri.pathSegments

        if (
            pathSegments.size != 2 ||
            pathSegments[0] !=
            URI_OCCURRENCE_PATH
        ) {
            return null
        }

        return pathSegments[1]
            .trim()
            .takeIf(
                String::isNotEmpty,
            )
    }
}
