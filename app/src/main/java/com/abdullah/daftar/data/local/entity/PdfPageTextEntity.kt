package com.abdullah.daftar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pdf_page_texts",
    foreignKeys = [
        ForeignKey(
            entity = PdfFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["fileId", "pageIndex"], unique = true)]
)
data class PdfPageTextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileId: Long,
    val pageIndex: Int,
    /** النص الأصلي كما استخرجه PdfBox (للعرض في المقتطفات). */
    val pageText: String,
    /** النص بعد التطبيع (NFKC، بلا تشكيل، ألف موحّدة...) — يُستخدم للبحث فقط. */
    val normalizedText: String
)
