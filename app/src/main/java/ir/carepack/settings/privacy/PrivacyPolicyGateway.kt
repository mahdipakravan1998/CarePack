package ir.carepack.settings.privacy

sealed interface OpenPrivacyPolicyResult {

    data object Opened :
        OpenPrivacyPolicyResult

    data object InvalidAddress :
        OpenPrivacyPolicyResult

    data object NoBrowser :
        OpenPrivacyPolicyResult

    data object Blocked :
        OpenPrivacyPolicyResult
}

fun interface PrivacyPolicyGateway {

    fun openPublicPolicy():
            OpenPrivacyPolicyResult
}
