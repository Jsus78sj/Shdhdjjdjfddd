package com.abdullah.daftar.data.storage

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedPdfMetadata(
    val displayName: String,
    val internalPath: String,
    val sizeBytes: Long,
    val pageCount: Int
)

@Singleton
class PdfStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun copyUriToInternalStorage(
        uri: Uri,
        subjectId: Long
    ): Result<ImportedPdfMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val originalName = queryDisplayName(uri) ?: "ملف_${System.currentTimeMillis()}.pdf"
            val cleanName = if (originalName.endsWith(".pdf", ignoreCase = true)) {
                originalName.substringBeforeLast(".")
            } else {
                originalName
            }

            val subjectDir = File(context.filesDir, "pdfs/subject_$subjectId").apply {
                if (!exists()) mkdirs()
            }

            val destFile = File(subjectDir, "${UUID.randomUUID()}.pdf")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: error("تعذر فتح الملف المختار")

            val pageCount = countPdfPages(destFile)
            ImportedPdfMetadata(
                displayName = cleanName,
                internalPath = destFile.absolutePath,
                sizeBytes = destFile.length(),
                pageCount = pageCount
            )
        }
    }

    /** اسم العرض لـ [uri] (لعرض أسماء الملفات الفاشلة في الاستيراد). */
    suspend fun displayNameOf(uri: Uri): String = withContext(Dispatchers.IO) {
        queryDisplayName(uri) ?: "ملف غير معروف"
    }

    suspend fun deleteInternalFile(path: String) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun deleteSubjectFolder(subjectId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            val subjectDir = File(context.filesDir, "pdfs/subject_$subjectId")
            if (subjectDir.exists()) {
                subjectDir.deleteRecursively()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun countPdfPages(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(1)
    }
}
