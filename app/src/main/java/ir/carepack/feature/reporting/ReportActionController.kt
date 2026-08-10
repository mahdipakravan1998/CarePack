package ir.carepack.feature.reporting

import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareDescriptor
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class ReportActionMessage {
    COPIED,
    SHARE_CHOOSER_OPENED,
}

internal enum class ReportActionFailure {
    COPY_FAILED,
    NO_SHARE_TARGET,
    SHARE_FAILED,
}

internal sealed interface ReportActionTransition {
    data object SharingStarted : ReportActionTransition
    data object SharingFinished : ReportActionTransition
    data class Succeeded(val message: ReportActionMessage) : ReportActionTransition
    data class Failed(val failure: ReportActionFailure) : ReportActionTransition
    data object MessageConsumed : ReportActionTransition
}

internal class ReportActionController(
    private val textShareGateway: TextShareGateway,
    private val scope: CoroutineScope,
    private val onTransition: (ReportActionTransition) -> Unit,
) {
    fun copy(reportText: String, descriptor: ShareDescriptor) {
        if (reportText.isBlank()) return

        when (textShareGateway.copy(reportText, descriptor)) {
            CopyTextResult.Copied ->
                onTransition(
                    ReportActionTransition.Succeeded(ReportActionMessage.COPIED),
                )
            CopyTextResult.Blocked,
            CopyTextResult.InvalidText,
            -> onTransition(
                ReportActionTransition.Failed(ReportActionFailure.COPY_FAILED),
            )
        }
    }

    fun share(
        reportText: String,
        descriptor: ShareDescriptor,
        isSharing: Boolean,
    ) {
        if (reportText.isBlank() || isSharing) return

        scope.launch {
            onTransition(ReportActionTransition.SharingStarted)
            try {
                when (textShareGateway.share(reportText, descriptor)) {
                    ShareTextResult.ChooserOpened ->
                        onTransition(
                            ReportActionTransition.Succeeded(
                                ReportActionMessage.SHARE_CHOOSER_OPENED,
                            ),
                        )
                    ShareTextResult.NoShareTarget ->
                        onTransition(
                            ReportActionTransition.Failed(
                                ReportActionFailure.NO_SHARE_TARGET,
                            ),
                        )
                    ShareTextResult.Blocked,
                    ShareTextResult.InvalidText,
                    -> onTransition(
                        ReportActionTransition.Failed(
                            ReportActionFailure.SHARE_FAILED,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                onTransition(
                    ReportActionTransition.Failed(ReportActionFailure.SHARE_FAILED),
                )
            } finally {
                onTransition(ReportActionTransition.SharingFinished)
            }
        }
    }

    fun consumeMessage() {
        onTransition(ReportActionTransition.MessageConsumed)
    }

}
