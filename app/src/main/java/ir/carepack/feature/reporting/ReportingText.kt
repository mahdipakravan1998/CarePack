package ir.carepack.feature.reporting

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.carepack.R
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.TemporalPhase

@Composable
internal fun reportStateText(reportState: CaregiverReportState?): String = when (reportState) {
    null -> stringResource(R.string.pr3_report_no_report)
    CaregiverReportState.GIVEN -> stringResource(R.string.pr3_report_given)
    CaregiverReportState.NOT_GIVEN -> stringResource(R.string.pr3_report_not_given)
    CaregiverReportState.UNKNOWN -> stringResource(R.string.pr3_report_unknown)
}

@Composable
internal fun temporalPhaseText(phase: TemporalPhase): String = when (phase) {
    TemporalPhase.UPCOMING -> stringResource(R.string.pr3_phase_upcoming)
    TemporalPhase.DUE -> stringResource(R.string.pr3_phase_due)
    TemporalPhase.PAST -> stringResource(R.string.pr3_phase_past)
}
