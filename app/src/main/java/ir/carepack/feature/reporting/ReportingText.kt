package ir.carepack.feature.reporting

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.carepack.R
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.TemporalStatus

@Composable
internal fun reportStateText(
    reportState: CaregiverReportState?,
): String =
    when (reportState) {
        null -> {
            stringResource(
                R.string.report_no_report,
            )
        }

        CaregiverReportState.GIVEN -> {
            stringResource(
                R.string.report_given,
            )
        }

        CaregiverReportState.NOT_GIVEN -> {
            stringResource(
                R.string.report_not_given,
            )
        }

        CaregiverReportState.UNKNOWN -> {
            stringResource(
                R.string.report_unknown,
            )
        }
    }

@Composable
internal fun temporalStatusText(
    phase: TemporalStatus,
): String =
    when (phase) {
        TemporalStatus.UPCOMING -> {
            stringResource(
                R.string.temporal_status_upcoming,
            )
        }

        TemporalStatus.DUE -> {
            stringResource(
                R.string.temporal_status_due,
            )
        }

        TemporalStatus.PAST -> {
            stringResource(
                R.string.temporal_status_past,
            )
        }
    }
