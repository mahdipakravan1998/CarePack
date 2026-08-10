package ir.carepack.core.text

internal fun String.normalizePersianAndArabicDigits(): String = map { character ->
        when (character) {
            '۰', '٠' -> '0'
            '۱', '١' -> '1'
            '۲', '٢' -> '2'
            '۳', '٣' -> '3'
            '۴', '٤' -> '4'
            '۵', '٥' -> '5'
            '۶', '٦' -> '6'
            '۷', '٧' -> '7'
            '۸', '٨' -> '8'
            '۹', '٩' -> '9'
            else -> character
        }
    }.joinToString(separator = "")
