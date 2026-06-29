package ir.carepack.reporting

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.platform.ExternalIntentLaunchResult
import ir.carepack.reporting.share.AndroidTextShareGateway
import ir.carepack.reporting.share.ClipboardWriter
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.settings.privacy.AndroidPrivacyPolicyGateway
import ir.carepack.settings.privacy.OpenPrivacyPolicyResult
import ir.carepack.testing.RecordingExternalIntentLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareAndPrivacyGatewayContractTest {

    private val context:
            Context =
        ApplicationProvider
            .getApplicationContext()

    @Test
    fun share_buildsExplicitChooserWithExactPlainText() {
        val launcher =
            RecordingExternalIntentLauncher()

        val gateway =
            AndroidTextShareGateway(
                context = context,
                externalIntentLauncher =
                    launcher,
                clipboardWriter =
                    RecordingClipboardWriter(),
            )

        assertTrue(
            launcher
                .launchedIntents
                .isEmpty(),
        )

        val result =
            gateway.share(
                text =
                    REPORT_TEXT,
            )

        assertEquals(
            ShareTextResult
                .ChooserOpened,
            result,
        )

        assertEquals(
            1,
            launcher
                .launchedIntents
                .size,
        )

        val chooserIntent =
            launcher
                .launchedIntents
                .single()

        /*
         * Xiaomi/MIUI may replace:
         *
         * android.intent.action.CHOOSER
         *
         * with:
         *
         * miui.intent.action.MIUI_CHOOSER
         *
         * Therefore the contract is verified through the wrapped
         * ACTION_SEND intent instead of requiring an OEM-specific
         * chooser action value.
         */
        @Suppress("DEPRECATION")
        val sendIntent =
            chooserIntent
                .getParcelableExtra<Intent>(
                    Intent.EXTRA_INTENT,
                )

        assertNotNull(
            sendIntent,
        )

        requireNotNull(
            sendIntent,
        )

        assertEquals(
            Intent.ACTION_SEND,
            sendIntent.action,
        )

        assertEquals(
            "text/plain",
            sendIntent.type,
        )

        assertEquals(
            REPORT_TEXT,
            sendIntent.getStringExtra(
                Intent.EXTRA_TEXT,
            ),
        )
    }

    @Test
    fun share_withoutHandler_isRecoverable() {
        val launcher =
            RecordingExternalIntentLauncher(
                result =
                    ExternalIntentLaunchResult
                        .NoHandler,
            )

        val gateway =
            AndroidTextShareGateway(
                context = context,
                externalIntentLauncher =
                    launcher,
                clipboardWriter =
                    RecordingClipboardWriter(),
            )

        assertEquals(
            ShareTextResult
                .NoShareTarget,
            gateway.share(
                text =
                    REPORT_TEXT,
            ),
        )
    }

    @Test
    fun copy_passesExactTextToClipboardWriter() {
        val clipboardWriter =
            RecordingClipboardWriter()

        val gateway =
            AndroidTextShareGateway(
                context = context,
                externalIntentLauncher =
                    RecordingExternalIntentLauncher(),
                clipboardWriter =
                    clipboardWriter,
            )

        val result =
            gateway.copy(
                text =
                    REPORT_TEXT,
            )

        assertEquals(
            CopyTextResult.Copied,
            result,
        )

        assertEquals(
            1,
            clipboardWriter
                .writes
                .size,
        )

        assertEquals(
            ClipboardWrite(
                label =
                    REPORT_CLIP_LABEL,
                text =
                    REPORT_TEXT,
            ),
            clipboardWriter
                .writes
                .single(),
        )
    }

    @Test
    fun copyFailure_isRecoverable() {
        val clipboardWriter =
            RecordingClipboardWriter(
                result = false,
            )

        val gateway =
            AndroidTextShareGateway(
                context = context,
                externalIntentLauncher =
                    RecordingExternalIntentLauncher(),
                clipboardWriter =
                    clipboardWriter,
            )

        assertEquals(
            CopyTextResult.Blocked,
            gateway.copy(
                text =
                    REPORT_TEXT,
            ),
        )
    }

    @Test
    fun validPrivacyUrl_opensExternalViewIntent() {
        val launcher =
            RecordingExternalIntentLauncher()

        val gateway =
            AndroidPrivacyPolicyGateway(
                context = context,
                policyUrl =
                    "https://example.org/privacy",
                externalIntentLauncher =
                    launcher,
            )

        assertEquals(
            OpenPrivacyPolicyResult.Opened,
            gateway.openPublicPolicy(),
        )

        val intent =
            launcher
                .launchedIntents
                .single()

        assertEquals(
            Intent.ACTION_VIEW,
            intent.action,
        )

        assertEquals(
            "https://example.org/privacy",
            intent.dataString,
        )
    }

    @Test
    fun invalidPrivacyUrl_doesNotLaunchAndRemainsRecoverable() {
        val launcher =
            RecordingExternalIntentLauncher()

        val gateway =
            AndroidPrivacyPolicyGateway(
                context = context,
                policyUrl = "",
                externalIntentLauncher =
                    launcher,
            )

        assertEquals(
            OpenPrivacyPolicyResult
                .InvalidAddress,
            gateway.openPublicPolicy(),
        )

        assertTrue(
            launcher
                .launchedIntents
                .isEmpty(),
        )
    }

    @Test
    fun privacyUrlWithoutBrowser_returnsNoBrowser() {
        val launcher =
            RecordingExternalIntentLauncher(
                result =
                    ExternalIntentLaunchResult
                        .NoHandler,
            )

        val gateway =
            AndroidPrivacyPolicyGateway(
                context = context,
                policyUrl =
                    "https://example.org/privacy",
                externalIntentLauncher =
                    launcher,
            )

        assertEquals(
            OpenPrivacyPolicyResult.NoBrowser,
            gateway.openPublicPolicy(),
        )
    }

    private companion object {

        const val REPORT_TEXT =
            "گزارش امروز آزمایشی"

        const val REPORT_CLIP_LABEL =
            "گزارش امروز کرپک"
    }
}

private data class ClipboardWrite(
    val label: String,
    val text: String,
)

private class RecordingClipboardWriter(
    private val result:
    Boolean = true,
) : ClipboardWriter {

    val writes =
        mutableListOf<ClipboardWrite>()

    override fun write(
        label: String,
        text: String,
    ): Boolean {
        writes +=
            ClipboardWrite(
                label = label,
                text = text,
            )

        return result
    }
}
