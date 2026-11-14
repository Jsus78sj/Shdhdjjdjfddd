package com.abdullah.daftar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pdf_highlights",
    foreignKeys = [
        ForeignKey(
            entity = PdfFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["fileId", "pageIndex"])]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileId: Long,
    val pageIndex: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val colorHex: Long,
    val createdAt: Long = System.currentTimeMillis()
)
