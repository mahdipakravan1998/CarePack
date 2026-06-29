package ir.carepack.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

sealed interface ExternalIntentLaunchResult {

    data object Launched :
        ExternalIntentLaunchResult

    data object NoHandler :
        ExternalIntentLaunchResult

    data object Blocked :
        ExternalIntentLaunchResult
}

fun interface ExternalIntentLauncher {

    fun launch(
        intent: Intent,
    ): ExternalIntentLaunchResult
}

class AndroidExternalIntentLauncher(
    context: Context,
) : ExternalIntentLauncher {

    private val applicationContext =
        context.applicationContext

    override fun launch(
        intent: Intent,
    ): ExternalIntentLaunchResult {
        val launchIntent =
            Intent(intent).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            )

        return try {
            applicationContext.startActivity(
                launchIntent,
            )

            ExternalIntentLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            ExternalIntentLaunchResult.NoHandler
        } catch (_: SecurityException) {
            ExternalIntentLaunchResult.Blocked
        }
    }
}
