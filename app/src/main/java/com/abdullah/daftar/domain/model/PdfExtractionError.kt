package com.abdullah.daftar.domain.model

/**
 * أسباب فشل استخراج نص PDF. تُخزَّن في عمود pdf_files.extractionError
 * (القيمة null تعني: لا خطأ).
 */
object PdfExtractionError {
    const val PASSWORD = "PASSWORD"
    const val CORRUPT = "CORRUPT"
    const val MISSING = "MISSING"
    const val MEMORY = "MEMORY"
    const val UNKNOWN = "UNKNOWN"

    fun message(code: String): String = when (code) {
        PASSWORD -> "الملف محمي بكلمة مرور، لا يمكن فهرسة نصه."
        CORRUPT -> "الملف تالف أو غير قابل للقراءة، تعذّرت فهرسة نصه."
        MISSING -> "ملف PDF غير موجود في التخزين، تعذّرت فهرسة نصه."
        MEMORY -> "الملف كبير جداً على ذاكرة الجهاز، تعذّرت فهرسة نصه."
        else -> "تعذّرت فهرسة نص هذا الملف."
    }
}
