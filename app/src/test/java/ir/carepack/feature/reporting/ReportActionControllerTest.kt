package ir.carepack.feature.reporting

import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareDescriptor
import ir.carepack.reporting.share.ShareReportKind
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportActionControllerTest {

    @Test
    fun share_mapsChooserAndBracketsWorkWithProgressTransitions() = runTest {
        val transitions = mutableListOf<ReportActionTransition>()
        val controller = ReportActionController(
            textShareGateway = SuccessfulGateway,
            scope = this,
            onTransition = transitions::add,
        )

        controller.share(
            reportText = "report",
            descriptor = ShareDescriptor(ShareReportKind.TODAY),
            isSharing = false,
        )
        runCurrent()

        assertEquals(
            listOf(
                ReportActionTransition.SharingStarted,
                ReportActionTransition.Succeeded(
                    ReportActionMessage.SHARE_CHOOSER_OPENED,
                ),
                ReportActionTransition.SharingFinished,
            ),
            transitions,
        )
    }

    @Test
    fun copy_mapsBlockedResultWithoutChangingFeatureSpecificWording() = runTest {
        val transitions = mutableListOf<ReportActionTransition>()
        val controller = ReportActionController(
            textShareGateway = BlockedGateway,
            scope = this,
            onTransition = transitions::add,
        )

        controller.copy("report", ShareDescriptor(ShareReportKind.TODAY))

        assertEquals(
            listOf(
                ReportActionTransition.Failed(ReportActionFailure.COPY_FAILED),
            ),
            transitions,
        )
    }
}

private object SuccessfulGateway : TextShareGateway {
    override fun share(text: String, descriptor: ShareDescriptor) =
        ShareTextResult.ChooserOpened

    override fun copy(text: String, descriptor: ShareDescriptor) =
        CopyTextResult.Copied
}

private object BlockedGateway : TextShareGateway {
    override fun share(text: String, descriptor: ShareDescriptor) =
        ShareTextResult.Blocked

    override fun copy(text: String, descriptor: ShareDescriptor) =
        CopyTextResult.Blocked
}
