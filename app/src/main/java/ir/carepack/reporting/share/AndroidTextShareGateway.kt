package ir.carepack.reporting.share

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import ir.carepack.platform.AndroidExternalIntentLauncher
import ir.carepack.platform.ExternalIntentLaunchResult
import ir.carepack.platform.ExternalIntentLauncher

fun interface ClipboardWriter {
    fun write(
        label: String,
        text: String,
    ): Boolean
}

class AndroidClipboardWriter(
    context: Context,
) : ClipboardWriter {
    private val applicationContext =
        context.applicationContext

    override fun write(
        label: String,
        text: String,
    ): Boolean {
        val clipboardManager =
            applicationContext.getSystemService(
                ClipboardManager::class.java,
            ) ?: return false

        val clipData = ClipData.newPlainText(label, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clipData.description.extras =
                PersistableBundle().apply {
                    putBoolean(
                        ClipDescription.EXTRA_IS_SENSITIVE,
                        true,
                    )
                }
        }

        return try {
            clipboardManager.setPrimaryClip(clipData)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}

class AndroidTextShareGateway(
    context: Context,
    private val externalIntentLauncher: ExternalIntentLauncher =
        AndroidExternalIntentLauncher(context),
    private val clipboardWriter: ClipboardWriter =
        AndroidClipboardWriter(context),
) : TextShareGateway {

    override fun share(
        text: String,
        descriptor: ShareDescriptor,
    ): ShareTextResult {
        if (text.isBlank()) {
            return ShareTextResult.InvalidText
        }

        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE_TEXT
                putExtra(Intent.EXTRA_TEXT, text)
            }

        val chooserIntent =
            Intent.createChooser(
                sendIntent,
                descriptor.chooserTitle,
            )

        return when (externalIntentLauncher.launch(chooserIntent)) {
            ExternalIntentLaunchResult.Launched ->
                ShareTextResult.ChooserOpened
            ExternalIntentLaunchResult.NoHandler ->
                ShareTextResult.NoShareTarget
            ExternalIntentLaunchResult.Blocked ->
                ShareTextResult.Blocked
        }
    }

    override fun copy(
        text: String,
        descriptor: ShareDescriptor,
    ): CopyTextResult {
        if (text.isBlank()) {
            return CopyTextResult.InvalidText
        }

        return if (
            clipboardWriter.write(
                label = descriptor.clipboardLabel,
                text = text,
            )
        ) {
            CopyTextResult.Copied
        } else {
            CopyTextResult.Blocked
        }
    }

    private companion object {
        const val MIME_TYPE_TEXT = "text/plain"
    }
}
