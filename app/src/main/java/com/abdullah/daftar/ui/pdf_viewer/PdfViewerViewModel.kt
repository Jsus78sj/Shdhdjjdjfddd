package com.abdullah.daftar.ui.pdf_viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.daftar.data.local.entity.BookmarkEntity
import com.abdullah.daftar.data.local.entity.HighlightEntity
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.local.entity.PdfPageTextEntity
import com.abdullah.daftar.data.repository.DaftarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DaftarRepository
) : ViewModel() {

    private val fileId: Long = checkNotNull(savedStateHandle["fileId"])
    val requestedInitialPage: Int = savedStateHandle.get<Int>("page") ?: -1

    private val inFileSearchQueryFlow = MutableStateFlow("")

    val pdfFileState: StateFlow<PdfFileEntity?> = repository.getFileById(fileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val bookmarksState: StateFlow<List<BookmarkEntity>> = repository.getBookmarksByFile(fileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val highlightsState: StateFlow<List<HighlightEntity>> = repository.getHighlightsByFile(fileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val inFileSearchResults: StateFlow<List<PdfPageTextEntity>> = inFileSearchQueryFlow
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchInsideSinglePdf(fileId, q.trim())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setInFileSearchQuery(query: String) {
        inFileSearchQueryFlow.value = query
    }

    fun triggerReindexFile() {
        repository.triggerTextExtractionForFile(fileId)
    }

    // ---- تقدّم القراءة: debounce للكتابة، وتحديث الودجت مرة واحدة عند الإغلاق ----
    private var pendingProgress: Pair<Int, Int>? = null // (pageIndex, totalPages)
    private var progressDebounceJob: Job? = null
    private var widgetNeedsRefresh = false
    private val progressMutex = Mutex()

    /** يسجّل آخر صفحة؛ تُكتب في القاعدة بعد ثانية من آخر تغيير (أو فوراً عبر [flushReadingProgress]). */
    fun saveReadingProgress(pageIndex: Int, totalPages: Int) {
        if (totalPages <= 0) return
        pendingProgress = pageIndex.coerceIn(0, totalPages - 1) to totalPages
        progressDebounceJob?.cancel()
        progressDebounceJob = viewModelScope.launch {
            delay(PROGRESS_DEBOUNCE_MS)
            // الكتابة نفسها غير قابلة للإلغاء كي لا تُقطع بتغيير صفحة جديد
            withContext(NonCancellable) { persistPendingProgress() }
        }
    }

    /**
     * يكتب أي تقدّم معلّق فوراً (عند onPause أو مغادرة الشاشة).
     * [refreshWidget] = true عند إغلاق القارئ: يحدّث الودجت مرة واحدة إن تغيّر شيء منذ آخر تحديث.
     */
    fun flushReadingProgress(refreshWidget: Boolean = false) {
        progressDebounceJob?.cancel()
        progressDebounceJob = null
        viewModelScope.launch {
            // NonCancellable: لا يضيع الحفظ إن أُلغي النطاق بعد المغادرة مباشرة
            withContext(NonCancellable) {
                persistPendingProgress()
                if (refreshWidget && widgetNeedsRefresh) {
                    widgetNeedsRefresh = false
                    repository.refreshWidgets()
                }
            }
        }
    }

    // الـ Mutex يُسلسل الكتابات: الـ flush ينتظر أي كتابة جارية قبل تحديث الودجت
    private suspend fun persistPendingProgress() = progressMutex.withLock {
        val (page, total) = pendingProgress ?: return@withLock
        pendingProgress = null
        repository.updateReadingProgress(fileId = fileId, lastPage = page, pageCount = total)
        widgetNeedsRefresh = true
    }

    private companion object {
        const val PROGRESS_DEBOUNCE_MS = 1000L
    }

    fun toggleFavorite() {
        val current = pdfFileState.value ?: return
        viewModelScope.launch {
            repository.toggleFileFavorite(current)
        }
    }

    fun addBookmark(pageIndex: Int, title: String) {
        viewModelScope.launch {
            repository.addBookmark(fileId, pageIndex, title)
        }
    }

    fun removeBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun addNoteForCurrentPage(pageIndex: Int, title: String, content: String) {
        val currentFile = pdfFileState.value ?: return
        viewModelScope.launch {
            repository.addNote(
                subjectId = currentFile.subjectId,
                title = title.ifBlank { "ملاحظة ص ${pageIndex + 1} - ${currentFile.displayName}" },
                content = content,
                linkedFileId = currentFile.id,
                linkedFileName = currentFile.displayName,
                linkedPage = pageIndex + 1
            )
        }
    }

    fun addHighlight(
        pageIndex: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        colorHex: Long
    ) {
        viewModelScope.launch {
            repository.addHighlight(
                fileId = fileId,
                pageIndex = pageIndex,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                colorHex = colorHex
            )
        }
    }

    fun undoLastHighlightOnPage(pageIndex: Int) {
        viewModelScope.launch {
            repository.undoLastHighlightOnPage(fileId, pageIndex)
        }
    }

    fun clearPageHighlights(pageIndex: Int) {
        viewModelScope.launch {
            repository.clearHighlightsOnPage(fileId, pageIndex)
        }
    }
}
