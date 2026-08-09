package ir.carepack.reminder.notification

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.carepack.MainActivity

object ReminderNotificationContract {
    const val CHANNEL_ID = "carepack_reminders"
    const val ACTION_OPEN_OCCURRENCE =
        "ir.carepack.action.OPEN_REMINDER_OCCURRENCE"
    const val ACTION_OPEN_REMINDER_SETTINGS =
        "ir.carepack.action.OPEN_REMINDER_SETTINGS"
    const val EXTRA_OCCURRENCE_ID =
        "ir.carepack.extra.OCCURRENCE_ID"

    private const val URI_SCHEME = "carepack"
    private const val URI_AUTHORITY = "reminder"
    private const val URI_OCCURRENCE_PATH = "occurrence"
    private const val URI_SETTINGS_PATH = "settings"

    fun createOpenOccurrenceIntent(
        context: Context,
        occurrenceId: String,
    ): Intent {
        require(occurrenceId.isNotBlank())

        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_OCCURRENCE
            data = createOccurrenceUri(occurrenceId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun createOpenReminderSettingsIntent(
        context: Context,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_REMINDER_SETTINGS
            data =
                Uri.Builder()
                    .scheme(URI_SCHEME)
                    .authority(URI_AUTHORITY)
                    .appendPath(URI_SETTINGS_PATH)
                    .build()
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun contentRequestCode(): Int = CONTENT_REQUEST_CODE
    fun testContentRequestCode(): Int = TEST_CONTENT_REQUEST_CODE

    fun extractOccurrenceId(
        intent: Intent?,
    ): String? {
        if (intent?.action != ACTION_OPEN_OCCURRENCE) {
            return null
        }

        val uriOccurrenceId =
            extractOccurrenceId(intent.data) ?: return null
        val extraOccurrenceId =
            intent
                .getStringExtra(EXTRA_OCCURRENCE_ID)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null

        return uriOccurrenceId.takeIf {
            it == extraOccurrenceId
        }
    }

    fun isOpenReminderSettingsIntent(
        intent: Intent?,
    ): Boolean =
        intent?.action == ACTION_OPEN_REMINDER_SETTINGS &&
            intent.data?.scheme == URI_SCHEME &&
            intent.data?.authority == URI_AUTHORITY &&
            intent.data?.pathSegments ==
            listOf(URI_SETTINGS_PATH)

    private fun createOccurrenceUri(
        occurrenceId: String,
    ): Uri =
        Uri.Builder()
            .scheme(URI_SCHEME)
            .authority(URI_AUTHORITY)
            .appendPath(URI_OCCURRENCE_PATH)
            .appendPath(occurrenceId)
            .build()

    private fun extractOccurrenceId(uri: Uri?): String? {
        if (
            uri?.scheme != URI_SCHEME ||
            uri.authority != URI_AUTHORITY
        ) {
            return null
        }

        val pathSegments = uri.pathSegments
        if (
            pathSegments.size != 2 ||
            pathSegments[0] != URI_OCCURRENCE_PATH
        ) {
            return null
        }

        return pathSegments[1]
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private const val CONTENT_REQUEST_CODE = 0x3210
    private const val TEST_CONTENT_REQUEST_CODE = 0x5A31
}
