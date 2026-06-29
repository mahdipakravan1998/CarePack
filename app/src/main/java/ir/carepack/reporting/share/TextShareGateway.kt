package ir.carepack.reporting.share

sealed interface ShareTextResult {

    data object ChooserOpened :
        ShareTextResult

    data object NoShareTarget :
        ShareTextResult

    data object Blocked :
        ShareTextResult

    data object InvalidText :
        ShareTextResult
}

sealed interface CopyTextResult {

    data object Copied :
        CopyTextResult

    data object Blocked :
        CopyTextResult

    data object InvalidText :
        CopyTextResult
}

interface TextShareGateway {

    fun share(
        text: String,
    ): ShareTextResult

    fun copy(
        text: String,
    ): CopyTextResult
}
