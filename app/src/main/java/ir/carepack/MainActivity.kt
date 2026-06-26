package ir.carepack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.carepack.app.ForegroundGenerationErrorHost
import ir.carepack.app.CarePackApp
import ir.carepack.ui.theme.CarePackTheme
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

    private var foregroundGenerationJob:
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

            CarePackTheme {
                ForegroundGenerationErrorHost(
                    errorMessage =
                        generationError,
                    onRetry =
                        ::guaranteeForegroundWindow,
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
                        clock =
                            container.clock,
                        zoneProvider =
                            container
                                .zoneProvider,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        guaranteeForegroundWindow()
    }

    private fun guaranteeForegroundWindow() {
        if (
            foregroundGenerationJob
                ?.isActive == true
        ) {
            return
        }

        foregroundGenerationJob =
            lifecycleScope.launch {
                foregroundGenerationError.value =
                    null

                try {
                    val now =
                        container
                            .clock
                            .instant()

                    val anchorDate =
                        now
                            .atZone(
                                container
                                    .zoneProvider
                                    .currentZone(),
                            )
                            .toLocalDate()

                    container
                        .occurrenceGenerator
                        .guaranteeWindowForAll(
                            anchorDate =
                                anchorDate,
                            now = now,
                        )
                } catch (_: Exception) {
                    foregroundGenerationError.value =
                        "به‌روزرسانی نوبت‌ها انجام نشد."
                }
            }
    }
}
