package ir.carepack.settings.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.carepack.platform.AndroidExternalIntentLauncher
import ir.carepack.platform.ExternalIntentLaunchResult
import ir.carepack.platform.ExternalIntentLauncher

class AndroidPrivacyPolicyGateway(
    context: Context,
    private val policyUrl: String,
    private val externalIntentLauncher:
    ExternalIntentLauncher =
        AndroidExternalIntentLauncher(
            context = context,
        ),
) : PrivacyPolicyGateway {

    override fun openPublicPolicy():
            OpenPrivacyPolicyResult {
        val policyUri =
            policyUrl.toVerifiedHttpsUri()
                ?: return OpenPrivacyPolicyResult
                    .InvalidAddress

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                policyUri,
            ).addCategory(
                Intent.CATEGORY_BROWSABLE,
            )

        return when (
            externalIntentLauncher.launch(
                intent,
            )
        ) {
            ExternalIntentLaunchResult.Launched -> {
                OpenPrivacyPolicyResult.Opened
            }

            ExternalIntentLaunchResult.NoHandler -> {
                OpenPrivacyPolicyResult.NoBrowser
            }

            ExternalIntentLaunchResult.Blocked -> {
                OpenPrivacyPolicyResult.Blocked
            }
        }
    }
}

private fun String.toVerifiedHttpsUri():
        Uri? {
    val normalized =
        trim()

    if (normalized.isEmpty()) {
        return null
    }

    val uri =
        runCatching {
            Uri.parse(normalized)
        }.getOrNull()
            ?: return null

    if (
        uri.scheme != HTTPS_SCHEME ||
        uri.host.isNullOrBlank()
    ) {
        return null
    }

    return uri
}

private const val HTTPS_SCHEME =
    "https"
