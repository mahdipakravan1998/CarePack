package ir.carepack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import ir.carepack.app.CarePackApp
import ir.carepack.ui.theme.CarePackTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container
        get() =
            (application as CarePackApplication)
                .container

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            CarePackTheme {
                CarePackApp(
                    carePlanService =
                        container.carePlanService,
                    todayQueryService =
                        container.todayQueryService,
                    caregiverReportService =
                        container.caregiverReportService,
                    setupPreferenceStore =
                        container.setupPreferenceStore,
                    clock = container.clock,
                    zoneProvider =
                        container.zoneProvider,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            runCatching {
                val now =
                    container.clock.instant()

                val deviceDate =
                    now
                        .atZone(
                            container
                                .zoneProvider
                                .currentZone(),
                        )
                        .toLocalDate()

                container
                    .occurrenceGenerator
                    .guaranteeForEffectiveSchedules(
                        anchorDate = deviceDate,
                        now = now,
                    )
            }
        }
    }
}
