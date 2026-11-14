package com.abdullah.daftar.ui.subject_detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.daftar.data.local.entity.NoteEntity
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.local.entity.SubjectEntity
import com.abdullah.daftar.data.repository.DaftarRepository
import com.abdullah.daftar.data.repository.FailedPdfImport
import com.abdullah.daftar.domain.model.PdfCategory
import com.abdullah.daftar.domain.model.PdfSortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectDetailUiState(
    val subject: SubjectEntity? = null,
    val allPdfFiles: List<PdfFileEntity> = emptyList(),
    val filteredPdfFiles: List<PdfFileEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val selectedCategory: PdfCategory? = null,
    val showFavoritesOnly: Boolean = false,
    val sortOption: PdfSortOption = PdfSortOption.DATE_ADDED,
    val isImporting: Boolean = false
)

@HiltViewModel
class SubjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DaftarRepository
) : ViewModel() {

    private val subjectId: Long = checkNotNull(savedStateHandle["subjectId"])

    private val selectedCategoryFlow = MutableStateFlow<PdfCategory?>(null)
    private val showFavoritesOnlyFlow = MutableStateFlow(false)
    private val sortOptionFlow = MutableStateFlow(PdfSortOption.DATE_ADDED)
    private val isImportingFlow = MutableStateFlow(false)
    private val importFailuresFlow = MutableStateFlow<List<FailedPdfImport>>(emptyList())

    /** الملفات التي فشل استيرادها في آخر عملية (فارغة = لا شيء لعرضه). */
    val importFailures: StateFlow<List<FailedPdfImport>> = importFailuresFlow

    private val filterConfigFlow = combine(
        selectedCategoryFlow,
        showFavoritesOnlyFlow,
        sortOptionFlow,
        isImportingFlow
    ) { category, favOnly, sortOption, isImporting ->
        FilterConfig(category, favOnly, sortOption, isImporting)
    }

    val uiState: StateFlow<SubjectDetailUiState> = combine(
        repository.getSubjectById(subjectId),
        repository.getFilesBySubject(subjectId),
        repository.getNotesBySubject(subjectId),
        filterConfigFlow
    ) { subject, files, notes, config ->
        val filtered = files
            .filter { file ->
                val matchesCategory = config.category == null || file.category == config.category.name
                val matchesFavorite = !config.favoritesOnly || file.isFavorite
                matchesCategory && matchesFavorite
            }
            .let { list ->
                when (config.sortOption) {
                    PdfSortOption.DATE_ADDED -> list.sortedByDescending { it.createdAt }
                    PdfSortOption.NAME -> list.sortedBy { it.displayName.lowercase() }
                    PdfSortOption.LAST_OPENED -> list.sortedWith(
                        compareByDescending<PdfFileEntity> { it.lastOpenedAt ?: 0L }
                            .thenByDescending { it.createdAt }
                    )
                }
            }

        SubjectDetailUiState(
            subject = subject,
            allPdfFiles = files,
            filteredPdfFiles = filtered,
            notes = notes,
            selectedCategory = config.category,
            showFavoritesOnly = config.favoritesOnly,
            sortOption = config.sortOption,
            isImporting = config.isImporting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubjectDetailUiState()
    )

    fun selectCategory(category: PdfCategory?) {
        selectedCategoryFlow.value = category
    }

    fun toggleFavoritesFilter() {
        showFavoritesOnlyFlow.value = !showFavoritesOnlyFlow.value
    }

    fun setSortOption(option: PdfSortOption) {
        sortOptionFlow.value = option
    }

    fun importPdfs(uris: List<Uri>, category: PdfCategory) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImportingFlow.value = true
            try {
                val result = repository.importPdfUris(subjectId, uris, category)
                if (result.failed.isNotEmpty()) importFailuresFlow.value = result.failed
            } finally {
                isImportingFlow.value = false
            }
        }
    }

    fun dismissImportFailures() {
        importFailuresFlow.value = emptyList()
    }

    fun updateFileNameAndCategory(file: PdfFileEntity, newName: String, category: PdfCategory) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateFileNameAndCategory(file.id, newName, category)
        }
    }

    fun toggleFileFavorite(file: PdfFileEntity) {
        viewModelScope.launch {
            repository.toggleFileFavorite(file)
        }
    }

    fun deletePdfFile(file: PdfFileEntity) {
        viewModelScope.launch {
            repository.deletePdfFile(file)
        }
    }

    fun addNote(
        title: String,
        content: String,
        linkedFile: PdfFileEntity?,
        linkedPage: Int?
    ) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            repository.addNote(
                subjectId = subjectId,
                title = title.ifBlank { "ملاحظة بدون عنوان" },
                content = content,
                linkedFileId = linkedFile?.id,
                linkedFileName = linkedFile?.displayName,
                linkedPage = linkedPage
            )
        }
    }

    fun updateNote(
        note: NoteEntity,
        title: String,
        content: String,
        linkedFile: PdfFileEntity?,
        linkedPage: Int?
    ) {
        viewModelScope.launch {
            repository.updateNote(
                note.copy(
                    title = title.ifBlank { "ملاحظة بدون عنوان" },
                    content = content,
                    linkedFileId = linkedFile?.id,
                    linkedFileName = linkedFile?.displayName,
                    linkedPage = linkedPage
                )
            )
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    private data class FilterConfig(
        val category: PdfCategory?,
        val favoritesOnly: Boolean,
        val sortOption: PdfSortOption,
        val isImporting: Boolean
    )
}
