package com.abdullah.daftar.domain.model

enum class PdfCategory(val arabicLabel: String) {
    LECTURES("محاضرات"),
    SUMMARIES("ملخصات"),
    PAST_EXAMS("اختبارات سابقة"),
    ASSIGNMENTS("واجبات"),
    OTHER("أخرى");

    companion object {
        fun fromName(name: String?): PdfCategory {
            return entries.find { it.name == name } ?: LECTURES
        }
    }
}
