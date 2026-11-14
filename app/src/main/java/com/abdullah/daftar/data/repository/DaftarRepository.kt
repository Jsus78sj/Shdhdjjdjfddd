package com.abdullah.daftar.data.repository

import android.content.Context
import android.net.Uri
import com.abdullah.daftar.data.local.dao.BookmarkDao
import com.abdullah.daftar.data.local.dao.HighlightDao
import com.abdullah.daftar.data.local.dao.NoteDao
import com.abdullah.daftar.data.local.dao.PdfContentSearchResult
import com.abdullah.daftar.data.local.dao.PdfFileDao
import com.abdullah.daftar.data.local.dao.PdfPageTextDao
import com.abdullah.daftar.data.local.dao.SemesterDao
import com.abdullah.daftar.data.local.dao.SubjectDao
import com.abdullah.daftar.data.local.entity.BookmarkEntity
import com.abdullah.daftar.data.local.entity.HighlightEntity
import com.abdullah.daftar.data.local.entity.NoteEntity
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.local.entity.PdfPageTextEntity
import com.abdullah.daftar.data.local.entity.SemesterEntity
import com.abdullah.daftar.data.local.entity.SubjectEntity
import com.abdullah.daftar.data.storage.PdfStorageHelper
import com.abdullah.daftar.data.worker.PdfTextExtractionWorker
import com.abdullah.daftar.domain.model.PdfCategory
import com.abdullah.daftar.ui.widget.DaftarAppWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.abdullah.daftar.data.text.ArabicTextNormalizer
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** ملف تم استيراده بنجاح. */
data class ImportedPdf(val fileId: Long, val displayName: String)

/** ملف فشل استيراده مع سبب مفهوم للمستخدم. */
data class FailedPdfImport(val displayName: String, val reason: String)

/** نتيجة [DaftarRepository.importPdfUris]: الناجحة والفاشلة معاً. */
data class PdfImportResult(
    val succeeded: List<ImportedPdf>,
    val failed: List<FailedPdfImport>
)

@Singleton
class DaftarRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val semesterDao: SemesterDao,
    private val subjectDao: SubjectDao,
    private val pdfFileDao: PdfFileDao,
    private val bookmarkDao: BookmarkDao,
    private val noteDao: NoteDao,
    private val pdfPageTextDao: PdfPageTextDao,
    private val highlightDao: HighlightDao,
    private val pdfStorageHelper: PdfStorageHelper
) {
    // --- الفصول الدراسية ---
    fun getAllSemesters(): Flow<List<SemesterEntity>> = semesterDao.getAllSemesters()

    fun getCurrentSemester(): Flow<SemesterEntity?> = semesterDao.getCurrentSemester()

    suspend fun ensureDefaultSemesterExists(): Long {
        if (semesterDao.getSemesterCount() == 0) {
            return semesterDao.insertSemester(
                SemesterEntity(
                    name = "الفصل الدراسي الأول",
                    isCurrent = true
                )
            )
        }
        return -1L
    }

    suspend fun addSemester(name: String, makeCurrent: Boolean = true): Long {
        if (makeCurrent) {
            semesterDao.clearCurrentSemesters()
        }
        return semesterDao.insertSemester(
            SemesterEntity(
                name = name.trim(),
                isCurrent = makeCurrent
            )
        )
    }

    suspend fun updateSemester(semester: SemesterEntity) {
        semesterDao.updateSemester(semester)
    }

    suspend fun setCurrentSemester(semesterId: Long) {
        semesterDao.setCurrentSemester(semesterId)
    }

    suspend fun setSemesterArchived(semesterId: Long, archived: Boolean) {
        semesterDao.setSemesterArchived(semesterId, archived)
    }

    suspend fun deleteSemester(semester: SemesterEntity) {
        // نجمع معرّفات المواد قبل الحذف لأن الـ CASCADE سيمسح صفوفها من القاعدة،
        // ثم نحذف مجلدات ملفات PDF الخاصة بها حتى لا تبقى ملفات يتيمة في التخزين.
        val subjectIds = subjectDao.getSubjectIdsBySemesterOnce(semester.id)
        semesterDao.deleteSemester(semester)
        subjectIds.forEach { pdfStorageHelper.deleteSubjectFolder(it) }
        DaftarAppWidget.updateAllWidgets(appContext)
    }

    // --- المواد الدراسية ---
    fun getSubjectsBySemester(semesterId: Long): Flow<List<SubjectEntity>> =
        subjectDao.getSubjectsBySemester(semesterId)

    fun getAllSubjects(): Flow<List<SubjectEntity>> = subjectDao.getAllSubjects()

    fun getSubjectById(subjectId: Long): Flow<SubjectEntity?> =
        subjectDao.getSubjectById(subjectId)

    fun searchSubjects(query: String): Flow<List<SubjectEntity>> =
        subjectDao.searchSubjects(escapeLike(query))

    suspend fun addSubject(
        semesterId: Long,
        name: String,
        code: String,
        professor: String,
        creditHours: Int,
        colorHex: Long
    ): Long {
        val id = subjectDao.insertSubject(
            SubjectEntity(
                semesterId = semesterId,
                name = name.trim(),
                code = code.trim(),
                professor = professor.trim(),
                creditHours = creditHours,
                colorHex = colorHex
            )
        )
        DaftarAppWidget.updateAllWidgets(appContext)
        return id
    }

    suspend fun updateSubject(subject: SubjectEntity) {
        subjectDao.updateSubject(subject)
        DaftarAppWidget.updateAllWidgets(appContext)
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        pdfStorageHelper.deleteSubjectFolder(subject.id)
        subjectDao.deleteSubject(subject)
        DaftarAppWidget.updateAllWidgets(appContext)
    }

    // --- ملفات PDF ---
    fun getFilesBySubject(subjectId: Long): Flow<List<PdfFileEntity>> =
        pdfFileDao.getFilesBySubject(subjectId)

    fun getAllFiles(): Flow<List<PdfFileEntity>> = pdfFileDao.getAllFiles()

    fun getFavoriteFiles(): Flow<List<PdfFileEntity>> = pdfFileDao.getFavoriteFiles()

    fun searchFiles(query: String): Flow<List<PdfFileEntity>> = pdfFileDao.searchFiles(escapeLike(query))

    fun getFileById(fileId: Long): Flow<PdfFileEntity?> =
        pdfFileDao.getFileById(fileId)

    suspend fun importPdfUris(
        subjectId: Long,
        uris: List<Uri>,
        category: PdfCategory = PdfCategory.LECTURES
    ): PdfImportResult {
        val succeeded = mutableListOf<ImportedPdf>()
        val failed = mutableListOf<FailedPdfImport>()
        for (uri in uris) {
            val copyResult = pdfStorageHelper.copyUriToInternalStorage(uri, subjectId)
            val meta = copyResult.getOrNull()
            if (meta == null) {
                failed += FailedPdfImport(
                    displayName = pdfStorageHelper.displayNameOf(uri),
                    reason = importFailureReason(copyResult.exceptionOrNull())
                )
                continue
            }
            val id = try {
                pdfFileDao.insertPdfFile(
                    PdfFileEntity(
                        subjectId = subjectId,
                        displayName = meta.displayName,
                        internalPath = meta.internalPath,
                        sizeBytes = meta.sizeBytes,
                        pageCount = meta.pageCount,
                        category = category.name,
                        isTextExtracted = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // فشل الإدراج: لا نُبقي النسخة المنسوخة يتيمة في التخزين الداخلي
                pdfStorageHelper.deleteInternalFile(meta.internalPath)
                failed += FailedPdfImport(meta.displayName, importFailureReason(e))
                continue
            }
            succeeded += ImportedPdf(id, meta.displayName)
            // المرحلة 5: جدولة استخراج النصوص في الخلفية عبر WorkManager + PdfBox
            PdfTextExtractionWorker.enqueueForFile(appContext, id)
        }
        DaftarAppWidget.updateAllWidgets(appContext)
        return PdfImportResult(succeeded, failed)
    }

    fun triggerTextExtractionForFile(fileId: Long) {
        PdfTextExtractionWorker.enqueueForFile(appContext, fileId)
    }

    fun triggerTextExtractionForAllUnindexed() {
        PdfTextExtractionWorker.enqueueAllUnindexed(appContext)
    }

    suspend fun updateFileNameAndCategory(fileId: Long, newName: String, category: PdfCategory) {
        pdfFileDao.updateFileNameAndCategory(fileId, newName.trim(), category.name)
        noteDao.updateLinkedFileName(fileId, newName.trim())
        DaftarAppWidget.updateAllWidgets(appContext)
    }

    suspend fun toggleFileFavorite(file: PdfFileEntity) {
        pdfFileDao.setFileFavorite(file.id, !file.isFavorite)
    }

    suspend fun updateReadingProgress(fileId: Long, lastPage: Int, pageCount: Int) {
        pdfFileDao.updateReadingProgress(
            fileId = fileId,
            lastPage = lastPage,
            pageCount = pageCount,
            openedAt = System.currentTimeMillis()
        )
        // لا يحدّث الودجت هنا (يُستدعى عند كل تغيير صفحة)؛ انظر refreshWidgets
    }

    /** يحدّث ودجت الشاشة الرئيسية؛ يُستدعى مرة واحدة عند إغلاق القارئ. */
    fun refreshWidgets() {
        DaftarAppWidget.updateAllWidgets(appContext)
    }

    suspend fun deletePdfFile(pdfFile: PdfFileEntity) {
        pdfStorageHelper.deleteInternalFile(pdfFile.internalPath)
        noteDao.clearFileLink(pdfFile.id)
        pdfFileDao.deletePdfFile(pdfFile)
        DaftarAppWidget.updateAllWidgets(appContext)
    }

    // --- الإشارات المرجعية داخل الملفات (Bookmarks) ---
    fun getBookmarksByFile(fileId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksByFile(fileId)

    suspend fun addBookmark(fileId: Long, pageIndex: Int, title: String) {
        bookmarkDao.insertBookmark(
            BookmarkEntity(
                fileId = fileId,
                pageIndex = pageIndex,
                title = title.trim().ifBlank { "صفحة ${pageIndex + 1}" }
            )
        )
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    // --- ملاحظات المادة (Subject Notes) ---
    fun getNotesBySubject(subjectId: Long): Flow<List<NoteEntity>> =
        noteDao.getNotesBySubject(subjectId)

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(escapeLike(query))

    suspend fun addNote(
        subjectId: Long,
        title: String,
        content: String,
        linkedFileId: Long? = null,
        linkedFileName: String? = null,
        linkedPage: Int? = null
    ): Long {
        val now = System.currentTimeMillis()
        return noteDao.insertNote(
            NoteEntity(
                subjectId = subjectId,
                title = title.trim(),
                content = content.trim(),
                linkedFileId = linkedFileId,
                linkedFileName = linkedFileName,
                linkedPage = linkedPage,
                updatedAt = now,
                createdAt = now
            )
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    // --- المرحلة 5: البحث داخل نصوص PDF ---
    // العبارة تُطبَّع بنفس قواعد الفهرسة (NFKC، بلا تشكيل، ألف موحّدة...) قبل مطابقتها
    fun searchInsideAllPdfContents(query: String): Flow<List<PdfContentSearchResult>> {
        val normalized = ArabicTextNormalizer.normalizeQuery(query)
        return if (normalized.isEmpty()) flowOf(emptyList())
        else pdfPageTextDao.searchInsideAllPdfContents(escapeLike(normalized)) // التهريب بعد التطبيع
    }

    fun searchInsideSinglePdf(fileId: Long, query: String): Flow<List<PdfPageTextEntity>> {
        val normalized = ArabicTextNormalizer.normalizeQuery(query)
        return if (normalized.isEmpty()) flowOf(emptyList())
        else pdfPageTextDao.searchInsideSinglePdf(fileId, escapeLike(normalized)) // التهريب بعد التطبيع
    }

    fun getIndexedPagesCount(): Flow<Int> = pdfPageTextDao.getIndexedPagesCount()

    // --- المرحلة 5: التظليل على صفحات PDF (Highlights) ---
    fun getHighlightsByFile(fileId: Long): Flow<List<HighlightEntity>> =
        highlightDao.getHighlightsByFile(fileId)

    fun getTotalHighlightsCount(): Flow<Int> = highlightDao.getTotalHighlightsCount()

    suspend fun addHighlight(
        fileId: Long,
        pageIndex: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        colorHex: Long
    ) {
        highlightDao.insertHighlight(
            HighlightEntity(
                fileId = fileId,
                pageIndex = pageIndex,
                startX = minOf(startX, endX).coerceIn(0f, 1f),
                startY = minOf(startY, endY).coerceIn(0f, 1f),
                endX = maxOf(startX, endX).coerceIn(0f, 1f),
                endY = maxOf(startY, endY).coerceIn(0f, 1f),
                colorHex = colorHex
            )
        )
    }

    suspend fun undoLastHighlightOnPage(fileId: Long, pageIndex: Int) {
        highlightDao.undoLastHighlightOnPage(fileId, pageIndex)
    }

    suspend fun clearHighlightsOnPage(fileId: Long, pageIndex: Int) {
        highlightDao.clearHighlightsOnPage(fileId, pageIndex)
    }
}

/** يهرّب \ و% و_ ليُعامَل النص حرفياً في LIKE ... ESCAPE '\'. */
private fun escapeLike(text: String): String = buildString(text.length + 8) {
    for (c in text) {
        if (c == '\\' || c == '%' || c == '_') append('\\')
        append(c)
    }
}

/** سبب فشل الاستيراد بصياغة مفهومة للمستخدم. */
private fun importFailureReason(t: Throwable?): String = when (t) {
    is SecurityException -> "لا توجد صلاحية لقراءة الملف"
    is FileNotFoundException -> "الملف غير موجود أو تعذّر الوصول إليه"
    is IOException -> "تعذّرت قراءة الملف أو نسخه إلى التطبيق"
    else -> t?.localizedMessage?.takeIf { it.isNotBlank() } ?: "سبب غير معروف"
}
