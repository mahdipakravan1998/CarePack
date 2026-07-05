package ir.carepack.reminder.permission

import android.content.Context
import android.os.Build
import android.os.PowerManager

enum class BatteryOptimizationState {
    IGNORED,
    NOT_IGNORED,
    UNKNOWN,
}

interface BatteryOptimizationGateway {

    fun currentState():
            BatteryOptimizationState
}

class AndroidBatteryOptimizationGateway(
    context: Context,
) : BatteryOptimizationGateway {

    private val applicationContext =
        context.applicationContext

    private val powerManager =
        applicationContext
            .getSystemService(
                PowerManager::class.java,
            )

    override fun currentState():
            BatteryOptimizationState {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.M ||
            powerManager == null
        ) {
            return BatteryOptimizationState.UNKNOWN
        }

        return try {
            if (
                powerManager
                    .isIgnoringBatteryOptimizations(
                        applicationContext.packageName,
                    )
            ) {
                BatteryOptimizationState.IGNORED
            } else {
                BatteryOptimizationState.NOT_IGNORED
            }
        } catch (_: RuntimeException) {
            BatteryOptimizationState.UNKNOWN
        }
    }
}
