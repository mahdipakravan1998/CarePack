package ir.carepack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.carepack.app.CarePackApp
import ir.carepack.app.ForegroundGenerationErrorHost
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.ui.theme.CarePackTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity :
    ComponentActivity() {

    private val container
        get() =
            (
                    application as
                            CarePackApplication
                    ).container

    private val foregroundGenerationError =
        MutableStateFlow<String?>(null)

    private val notificationOccurrenceId =
        MutableStateFlow<String?>(null)

    private var foregroundReconciliationJob:
            Job? = null

    private var notificationValidationJob:
            Job? = null

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val generationError =
                foregroundGenerationError
                    .collectAsStateWithLifecycle()
                    .value

            val pendingNotificationOccurrenceId =
                notificationOccurrenceId
                    .collectAsStateWithLifecycle()
                    .value

            CarePackTheme {
                ForegroundGenerationErrorHost(
                    errorMessage =
                        generationError,
                    onRetry =
                        ::reconcileForegroundState,
                ) {
                    CarePackApp(
                        carePlanService =
                            container
                                .carePlanService,
                        todayQueryService =
                            container
                                .todayQueryService,
                        caregiverReportService =
                            container
                                .caregiverReportService,
                        setupPreferenceStore =
                            container
                                .setupPreferenceStore,
                        reminderPreferenceStore =
                            container
                                .reminderPreferenceStore,
                        reminderCoordinator =
                            container
                                .reminderCoordinator,
                        notificationPermissionGateway =
                            container
                                .notificationPermissionGateway,
                        clock =
                            container.clock,
                        zoneProvider =
                            container
                                .zoneProvider,
                        notificationOccurrenceId =
                            pendingNotificationOccurrenceId,
                        onNotificationOccurrenceHandled = {
                            notificationOccurrenceId.value =
                                null
                        },
                    )
                }
            }
        }

        validateNotificationIntent(
            sourceIntent = intent,
        )
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(intent)
        setIntent(intent)

        validateNotificationIntent(
            sourceIntent = intent,
        )
    }

    override fun onStart() {
        super.onStart()

        reconcileForegroundState()
    }

    private fun reconcileForegroundState() {
        if (
            foregroundReconciliationJob
                ?.isActive == true
        ) {
            return
        }

        foregroundReconciliationJob =
            lifecycleScope.launch {
                foregroundGenerationError.value =
                    null

                try {
                    container
                        .appReconciler
                        .reconcile(
                            reason =
                                ReconciliationReason
                                    .APPLICATION_FOREGROUND,
                        )
                } catch (
                    cancellation:
                    CancellationException,
                ) {
                    throw cancellation
                } catch (_: Exception) {
                    foregroundGenerationError.value =
                        "به‌روزرسانی نوبت‌ها و یادآورها انجام نشد."
                }
            }
    }

    private fun validateNotificationIntent(
        sourceIntent: Intent?,
    ) {
        notificationValidationJob
            ?.cancel()

        notificationValidationJob =
            lifecycleScope.launch {
                notificationOccurrenceId.value =
                    container
                        .notificationNavigationValidator
                        .validatedOccurrenceId(
                            intent = sourceIntent,
                        )
            }
    }
}
