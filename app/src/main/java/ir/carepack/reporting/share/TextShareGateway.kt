package ir.carepack.reporting.share

enum class ShareReportKind {
    TODAY,
    SEVEN_DAY,
    THIRTY_DAY,
    CUSTOM_RANGE,
}

data class ShareDescriptor(
    val kind: ShareReportKind,
) {
    val chooserTitle: String
        get() = when (kind) {
                ShareReportKind.TODAY ->
                    "اشتراک‌گذاری گزارش امروز CarePack"
                ShareReportKind.SEVEN_DAY ->
                    "اشتراک‌گذاری گزارش ۷ روزه CarePack"
                ShareReportKind.THIRTY_DAY ->
                    "اشتراک‌گذاری گزارش ۳۰ روزه CarePack"
                ShareReportKind.CUSTOM_RANGE ->
                    "اشتراک‌گذاری گزارش بازه CarePack"
            }

    val clipboardLabel: String
        get() = when (kind) {
                ShareReportKind.TODAY ->
                    "گزارش امروز CarePack"
                ShareReportKind.SEVEN_DAY ->
                    "گزارش ۷ روزه CarePack"
                ShareReportKind.THIRTY_DAY ->
                    "گزارش ۳۰ روزه CarePack"
                ShareReportKind.CUSTOM_RANGE ->
                    "گزارش بازه CarePack"
            }
}

sealed interface ShareTextResult {
    data object ChooserOpened : ShareTextResult
    data object NoShareTarget : ShareTextResult
    data object Blocked : ShareTextResult
    data object InvalidText : ShareTextResult
}

sealed interface CopyTextResult {
    data object Copied : CopyTextResult
    data object Blocked : CopyTextResult
    data object InvalidText : CopyTextResult
}

interface TextShareGateway {
    fun share(
        text: String,
        descriptor: ShareDescriptor,
    ): ShareTextResult

    fun copy(
        text: String,
        descriptor: ShareDescriptor,
    ): CopyTextResult
}
