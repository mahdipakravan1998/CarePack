package ir.carepack.reporting

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.platform.ExternalIntentLaunchResult
import ir.carepack.reporting.share.AndroidTextShareGateway
import ir.carepack.reporting.share.ClipboardWriter
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.testing.RecordingExternalIntentLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextShareGatewayContractTest {

    @Test
    fun share_usesAndroidChooserWithPlainTextSendIntent() {
        val launcher =
            RecordingExternalIntentLauncher()

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    launcher,
                clipboardWriter =
                    RecordingClipboardWriter(),
            )

        val result =
            gateway.share(
                REPORT_TEXT,
            )

        assertEquals(
            ShareTextResult.ChooserOpened,
            result,
        )

        assertEquals(
            1,
            launcher.launchedIntents.size,
        )

        val chooserIntent =
            launcher.launchedIntents.single()

        assertTrue(
            "Unexpected chooser action: ${chooserIntent.action}",
            chooserIntent.action ==
                    Intent.ACTION_CHOOSER ||
                    chooserIntent.action ==
                    MIUI_CHOOSER_ACTION,
        )

        @Suppress("DEPRECATION")
        val sendIntent =
            chooserIntent.getParcelableExtra<Intent>(
                Intent.EXTRA_INTENT,
            )

        assertNotNull(
            sendIntent,
        )

        assertEquals(
            Intent.ACTION_SEND,
            sendIntent?.action,
        )

        assertEquals(
            "text/plain",
            sendIntent?.type,
        )

        assertEquals(
            REPORT_TEXT,
            sendIntent?.getStringExtra(
                Intent.EXTRA_TEXT,
            ),
        )
    }

    @Test
    fun shareBlankText_isRejectedWithoutLaunchingChooser() {
        val launcher =
            RecordingExternalIntentLauncher()

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    launcher,
                clipboardWriter =
                    RecordingClipboardWriter(),
            )

        val result =
            gateway.share(
                "   ",
            )

        assertEquals(
            ShareTextResult.InvalidText,
            result,
        )

        assertTrue(
            launcher.launchedIntents.isEmpty(),
        )
    }

    @Test
    fun shareLauncherNoHandler_mapsToNoShareTarget() {
        val launcher =
            RecordingExternalIntentLauncher(
                result =
                    ExternalIntentLaunchResult
                        .NoHandler,
            )

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    launcher,
                clipboardWriter =
                    RecordingClipboardWriter(),
            )

        assertEquals(
            ShareTextResult.NoShareTarget,
            gateway.share(
                REPORT_TEXT,
            ),
        )
    }

    @Test
    fun shareLauncherBlocked_mapsToBlocked() {
        val launcher =
            RecordingExternalIntentLauncher(
                result =
                    ExternalIntentLaunchResult
                        .Blocked,
            )

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    launcher,
                clipboardWriter =
                    RecordingClipboardWriter(),
            )

        assertEquals(
            ShareTextResult.Blocked,
            gateway.share(
                REPORT_TEXT,
            ),
        )
    }

    @Test
    fun copyWritesSensitivePlainTextThroughClipboardWriter() {
        val clipboardWriter =
            RecordingClipboardWriter()

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    RecordingExternalIntentLauncher(),
                clipboardWriter =
                    clipboardWriter,
            )

        val result =
            gateway.copy(
                REPORT_TEXT,
            )

        assertEquals(
            CopyTextResult.Copied,
            result,
        )

        assertEquals(
            listOf(
                REPORT_TEXT,
            ),
            clipboardWriter.writtenTexts,
        )

        assertEquals(
            listOf(
                "گزارش امروز کرپک",
            ),
            clipboardWriter.labels,
        )
    }

    @Test
    fun copyBlankText_isRejectedWithoutWritingClipboard() {
        val clipboardWriter =
            RecordingClipboardWriter()

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    RecordingExternalIntentLauncher(),
                clipboardWriter =
                    clipboardWriter,
            )

        val result =
            gateway.copy(
                "   ",
            )

        assertEquals(
            CopyTextResult.InvalidText,
            result,
        )

        assertTrue(
            clipboardWriter.writtenTexts.isEmpty(),
        )
    }

    @Test
    fun copyFailure_mapsToBlocked() {
        val clipboardWriter =
            RecordingClipboardWriter(
                writeResult = false,
            )

        val gateway =
            AndroidTextShareGateway(
                context =
                    ApplicationProvider
                        .getApplicationContext(),
                externalIntentLauncher =
                    RecordingExternalIntentLauncher(),
                clipboardWriter =
                    clipboardWriter,
            )

        assertEquals(
            CopyTextResult.Blocked,
            gateway.copy(
                REPORT_TEXT,
            ),
        )
    }

    private companion object {
        const val REPORT_TEXT =
            "گزارش امروز کرپک"

        const val MIUI_CHOOSER_ACTION =
            "miui.intent.action.MIUI_CHOOSER"
    }
}

private class RecordingClipboardWriter(
    private val writeResult: Boolean = true,
) : ClipboardWriter {

    val labels =
        mutableListOf<String>()

    val writtenTexts =
        mutableListOf<String>()

    override fun write(
        label: String,
        text: String,
    ): Boolean {
        labels += label
        writtenTexts += text

        return writeResult
    }
}
