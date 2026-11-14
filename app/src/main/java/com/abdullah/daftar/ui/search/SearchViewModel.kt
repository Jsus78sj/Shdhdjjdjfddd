package com.abdullah.daftar.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.daftar.data.local.dao.PdfContentSearchResult
import com.abdullah.daftar.data.local.entity.NoteEntity
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.local.entity.SubjectEntity
import com.abdullah.daftar.data.repository.DaftarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val subjects: List<SubjectEntity> = emptyList(),
    val files: List<PdfFileEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val pdfContentMatches: List<PdfContentSearchResult> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DaftarRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    // النتائج تتبع الاستعلام بعد debounce؛ أما حقل الإدخال فيقرأ الاستعلام الحالي مباشرة (انظر uiState)
    private val resultsFlow: Flow<SearchUiState> = queryFlow
        .debounce(DEBOUNCE_MS)
        .flatMapLatest { rawQuery ->
            val q = rawQuery.trim()
            // الاستعلام الأقصر من حرفين يعرض الحالة الفارغة (الافتراضية)
            if (q.length < MIN_QUERY_LENGTH) {
                combine(
                    repository.getAllSubjects(),
                    repository.getFavoriteFiles(),
                    repository.getAllNotes()
                ) { subjects, favFiles, notes ->
                    SearchUiState(
                        query = rawQuery,
                        subjects = subjects.take(6),
                        files = favFiles,
                        notes = notes.take(6),
                        pdfContentMatches = emptyList()
                    )
                }
            } else {
                combine(
                    repository.searchSubjects(q),
                    repository.searchFiles(q),
                    repository.searchNotes(q),
                    repository.searchInsideAllPdfContents(q)
                ) { subjects, files, notes, contentMatches ->
                    SearchUiState(
                        query = rawQuery,
                        subjects = subjects,
                        files = files,
                        notes = notes,
                        pdfContentMatches = contentMatches
                    )
                }
            }
        }

    // query هنا هو النص الحالي في الحقل دون تأخير، لئلا يتأخر ظهور ما يكتبه المستخدم 300ms
    val uiState: StateFlow<SearchUiState> = combine(
        queryFlow,
        resultsFlow.onStart { emit(SearchUiState()) }
    ) { liveQuery, results -> results.copy(query = liveQuery) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState()
        )

    fun onQueryChange(newQuery: String) {
        queryFlow.value = newQuery
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }
}
