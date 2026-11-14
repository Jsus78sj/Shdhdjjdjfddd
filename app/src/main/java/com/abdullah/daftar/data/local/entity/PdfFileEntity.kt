package com.abdullah.daftar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abdullah.daftar.domain.model.PdfCategory

@Entity(
    tableName = "pdf_files",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId"])]
)
data class PdfFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val displayName: String,
    val internalPath: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val category: String = PdfCategory.LECTURES.name,
    val isFavorite: Boolean = false,
    val isTextExtracted: Boolean = false,
    /** سبب فشل استخراج النص (انظر PdfExtractionError)، أو null إن لم يفشل. */
    val extractionError: String? = null,
    val lastReadPage: Int = 0,
    val lastOpenedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
