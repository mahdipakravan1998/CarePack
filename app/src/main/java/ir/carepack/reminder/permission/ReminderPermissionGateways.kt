package ir.carepack.reminder.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

interface NotificationPermissionGateway {

    fun isPermissionGranted():
            Boolean

    fun requiresRuntimePermission():
            Boolean
}

class AndroidNotificationPermissionGateway(
    context: Context,
) : NotificationPermissionGateway {

    private val applicationContext =
        context.applicationContext

    override fun isPermissionGranted():
            Boolean {
        if (!requiresRuntimePermission()) {
            return true
        }

        return ContextCompat
            .checkSelfPermission(
                applicationContext,
                Manifest.permission
                    .POST_NOTIFICATIONS,
            ) ==
                PackageManager
                    .PERMISSION_GRANTED
    }

    override fun requiresRuntimePermission():
            Boolean {
        return Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
    }
}

interface ExactAlarmCapabilityGateway {

    fun canScheduleExactAlarms():
            Boolean
}

class AndroidExactAlarmCapabilityGateway(
    context: Context,
) : ExactAlarmCapabilityGateway {

    private val alarmManager =
        checkNotNull(
            context
                .applicationContext
                .getSystemService(
                    AlarmManager::class.java,
                ),
        )

    override fun canScheduleExactAlarms():
            Boolean {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            alarmManager
                .canScheduleExactAlarms()
        } else {
            true
        }
    }
}
