package com.abdullah.daftar.data.worker

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.abdullah.daftar.data.local.DaftarDatabase
import com.abdullah.daftar.data.local.dao.PdfFileDao
import com.abdullah.daftar.data.local.dao.PdfPageTextDao
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.local.entity.PdfPageTextEntity
import com.abdullah.daftar.data.text.ArabicTextNormalizer
import com.abdullah.daftar.domain.model.PdfExtractionError
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * يستخرج نص كل صفحة من ملفات PDF ويفهرسه للبحث.
 *
 * سياسة الأخطاء (لا إعادة محاولة بلا نهاية):
 *  - ملف بكلمة مرور / تالف / مفقود / أكبر من الذاكرة: فشل دائم؛ يُعلَّم الملف بـ extractionError
 *    ولا تُعاد فهرسته تلقائياً، ويستمر العمل على بقية الملفات.
 *  - خطأ عابر غير متوقع (مثلاً قاعدة البيانات): Result.retry() بحد أقصى [MAX_ATTEMPTS] محاولات،
 *    وبعدها يُعلَّم الملف فاشلاً (UNKNOWN).
 *  - عند فهرسة ملف واحد وفشله فشلاً دائماً يرجع العمل Result.failure مع سبب الفشل.
 */
class PdfTextExtractionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun database(): DaftarDatabase
        fun pdfFileDao(): PdfFileDao
        fun pdfPageTextDao(): PdfPageTextDao
    }

    private sealed interface Outcome {
        data object Indexed : Outcome
        data class Failed(val reason: String) : Outcome
        data object Transient : Outcome
    }

    private class Setup(
        val database: DaftarDatabase,
        val pdfFileDao: PdfFileDao,
        val pdfPageTextDao: PdfPageTextDao,
        val files: List<PdfFileEntity>
    )

    private suspend fun prepare(targetFileId: Long): Setup {
        PDFBoxResourceLoader.init(applicationContext)
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val pdfFileDao = entryPoint.pdfFileDao()
        val files = if (targetFileId > 0L) {
            listOfNotNull(pdfFileDao.getFileByIdOnce(targetFileId))
        } else {
            pdfFileDao.getUnindexedFilesOnce()
        }
        return Setup(entryPoint.database(), pdfFileDao, entryPoint.pdfPageTextDao(), files)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetFileId = inputData.getLong(KEY_FILE_ID, -1L)
        val isSingleFile = targetFileId > 0L

        val setup = try {
            prepare(targetFileId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext if (canRetry()) Result.retry() else Result.failure()
        }
        val database = setup.database
        val pdfFileDao = setup.pdfFileDao
        val pdfPageTextDao = setup.pdfPageTextDao
        val filesToProcess = setup.files

        var hasTransientFailure = false
        var singleFileFailure: String? = null

        for (pdfEntity in filesToProcess) {
            currentCoroutineContext().ensureActive()
            when (val outcome = processFile(pdfEntity, database, pdfFileDao, pdfPageTextDao)) {
                Outcome.Indexed -> Unit
                Outcome.Transient -> hasTransientFailure = true
                is Outcome.Failed -> {
                    // فشل علامة الفشل نفسها (قاعدة بيانات) لا يجوز أن يوقف بقية الملفات
                    try {
                        pdfFileDao.setTextExtractionResult(pdfEntity.id, false, outcome.reason)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        hasTransientFailure = true
                    }
                    if (isSingleFile) singleFileFailure = outcome.reason
                }
            }
        }

        when {
            hasTransientFailure && canRetry() -> Result.retry()
            singleFileFailure != null ->
                Result.failure(workDataOf(KEY_ERROR to singleFileFailure))
            else -> Result.success()
        }
    }

    /** هل بقيت محاولات؟ runAttemptCount يبدأ من 0. */
    private fun canRetry(): Boolean = runAttemptCount + 1 < MAX_ATTEMPTS

    private suspend fun processFile(
        pdfEntity: PdfFileEntity,
        database: DaftarDatabase,
        pdfFileDao: PdfFileDao,
        pdfPageTextDao: PdfPageTextDao
    ): Outcome {
        val file = File(pdfEntity.internalPath)
        if (!file.exists()) return Outcome.Failed(PdfExtractionError.MISSING)

        return try {
            // الاستخراج أولاً في الذاكرة؛ فلا نمسح الفهرس القديم إلا بعد نجاح الاستخراج
            val pages = extractPages(pdfEntity.id, file)
            database.withTransaction {
                pdfPageTextDao.clearTextForFile(pdfEntity.id)
                if (pages.isNotEmpty()) pdfPageTextDao.insertPageTexts(pages)
                pdfFileDao.setTextExtractionResult(pdfEntity.id, true, null)
            }
            Outcome.Indexed
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidPasswordException) {
            Outcome.Failed(PdfExtractionError.PASSWORD)
        } catch (e: IOException) {
            // أخطاء قراءة/تحليل PDF من PdfBox (ترويسة تالفة، جدول مراجع معطوب...)
            Outcome.Failed(PdfExtractionError.CORRUPT)
        } catch (e: OutOfMemoryError) {
            Outcome.Failed(PdfExtractionError.MEMORY)
        } catch (e: Exception) {
            if (canRetry()) Outcome.Transient else Outcome.Failed(PdfExtractionError.UNKNOWN)
        }
    }

    private suspend fun extractPages(fileId: Long, file: File): List<PdfPageTextEntity> {
        val pages = ArrayList<PdfPageTextEntity>()
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            val totalPages = document.numberOfPages
            var failedPages = 0

            for (pageNum in 1..totalPages) {
                currentCoroutineContext().ensureActive()
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val rawText = try {
                    stripper.getText(document).trim()
                } catch (e: Exception) {
                    failedPages++
                    ""
                }
                if (rawText.isBlank()) continue

                val normalized = ArabicTextNormalizer.normalize(rawText)
                if (normalized.isBlank()) continue
                pages.add(
                    PdfPageTextEntity(
                        fileId = fileId,
                        pageIndex = pageNum - 1,
                        pageText = rawText,
                        normalizedText = normalized
                    )
                )
            }

            // فشلت كل الصفحات = الملف غير قابل للقراءة فعلياً (وليس مجرد صفحات ممسوحة بلا نص)
            if (totalPages > 0 && failedPages == totalPages) {
                throw IOException("تعذّرت قراءة أي صفحة من الملف")
            }
        }
        return pages
    }

    companion object {
        const val KEY_FILE_ID = "file_id"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 3

        fun enqueueForFile(context: Context, fileId: Long) {
            val request = OneTimeWorkRequestBuilder<PdfTextExtractionWorker>()
                .setInputData(workDataOf(KEY_FILE_ID to fileId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "extract_pdf_text_$fileId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueAllUnindexed(context: Context) {
            val request = OneTimeWorkRequestBuilder<PdfTextExtractionWorker>()
                .setInputData(workDataOf(KEY_FILE_ID to -1L))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "extract_all_unindexed_pdfs",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
