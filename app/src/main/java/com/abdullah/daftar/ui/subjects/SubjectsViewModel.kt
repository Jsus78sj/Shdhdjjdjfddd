package com.abdullah.daftar.ui.subjects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.daftar.data.local.entity.SemesterEntity
import com.abdullah.daftar.data.local.entity.SubjectEntity
import com.abdullah.daftar.data.repository.DaftarRepository
import com.abdullah.daftar.data.repository.FailedPdfImport
import com.abdullah.daftar.domain.model.PdfCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectsUiState(
    val allSemesters: List<SemesterEntity> = emptyList(),
    val visibleSemesters: List<SemesterEntity> = emptyList(),
    val showArchivedSemesters: Boolean = false,
    val selectedSemester: SemesterEntity? = null,
    val subjects: List<SubjectEntity> = emptyList(),
    val allSubjects: List<SubjectEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val repository: DaftarRepository
) : ViewModel() {

    private val selectedSemesterId = MutableStateFlow<Long?>(null)
    private val showArchivedFlow = MutableStateFlow(false)

    private val isImportingSharedFlow = MutableStateFlow(false)
    private val importFailuresFlow = MutableStateFlow<List<FailedPdfImport>>(emptyList())

    /** true أثناء استيراد ملفات المشاركة (لتعطيل أزرار نافذة المشاركة). */
    val isImportingShared: StateFlow<Boolean> = isImportingSharedFlow

    /** الملفات التي فشل استيرادها في آخر عملية مشاركة (فارغة = لا شيء لعرضه). */
    val importFailures: StateFlow<List<FailedPdfImport>> = importFailuresFlow

    /** عدد ملفات PDF لكل مادة (لنوافذ تأكيد الحذف). */
    val fileCountBySubject: StateFlow<Map<Long, Int>> = repository.getAllFiles()
        .map { files -> files.groupingBy { it.subjectId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** عدد الملاحظات لكل مادة (لنوافذ تأكيد الحذف). */
    val noteCountBySubject: StateFlow<Map<Long, Int>> = repository.getAllNotes()
        .map { notes -> notes.groupingBy { it.subjectId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val semestersFlow = repository.getAllSemesters()
    private val allSubjectsFlow = repository.getAllSubjects()

    init {
        viewModelScope.launch {
            repository.ensureDefaultSemesterExists()
        }
    }

    val uiState: StateFlow<SubjectsUiState> = combine(
        semestersFlow,
        selectedSemesterId,
        showArchivedFlow,
        allSubjectsFlow
    ) { semesters, chosenId, showArchived, allSubjects ->
        val visible = if (showArchived) semesters else semesters.filter { !it.isArchived }
        val activeSemester = visible.find { it.id == chosenId }
            ?: visible.find { it.isCurrent }
            ?: visible.firstOrNull()
            ?: semesters.firstOrNull()
        Tuple4(semesters, visible, showArchived, activeSemester to allSubjects)
    }.flatMapLatest { (allSemesters, visibleSemesters, showArchived, pair) ->
        val (activeSemester, allSubjects) = pair
        if (activeSemester == null) {
            flowOf(
                SubjectsUiState(
                    allSemesters = allSemesters,
                    visibleSemesters = visibleSemesters,
                    showArchivedSemesters = showArchived,
                    allSubjects = allSubjects
                )
            )
        } else {
            repository.getSubjectsBySemester(activeSemester.id).combine(
                flowOf(activeSemester)
            ) { subjects, activeSem ->
                SubjectsUiState(
                    allSemesters = allSemesters,
                    visibleSemesters = visibleSemesters,
                    showArchivedSemesters = showArchived,
                    selectedSemester = activeSem,
                    subjects = subjects,
                    allSubjects = allSubjects
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubjectsUiState()
    )

    fun selectSemester(semesterId: Long) {
        selectedSemesterId.value = semesterId
        viewModelScope.launch {
            repository.setCurrentSemester(semesterId)
        }
    }

    fun toggleShowArchivedSemesters() {
        showArchivedFlow.value = !showArchivedFlow.value
    }

    fun addSemester(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.addSemester(name, makeCurrent = true)
            selectedSemesterId.value = newId
        }
    }

    fun updateSemesterName(semester: SemesterEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateSemester(semester.copy(name = newName.trim()))
        }
    }

    fun toggleSemesterArchive(semester: SemesterEntity) {
        viewModelScope.launch {
            val newArchived = !semester.isArchived
            repository.setSemesterArchived(semester.id, newArchived)
            if (newArchived && selectedSemesterId.value == semester.id) {
                selectedSemesterId.value = null
            }
        }
    }

    fun deleteSemester(semester: SemesterEntity) {
        viewModelScope.launch {
            repository.deleteSemester(semester)
            if (selectedSemesterId.value == semester.id) {
                selectedSemesterId.value = null
            }
        }
    }

    fun addSubject(
        name: String,
        code: String,
        professor: String,
        creditHours: Int,
        colorHex: Long
    ) {
        val currentSem = uiState.value.selectedSemester ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addSubject(
                semesterId = currentSem.id,
                name = name,
                code = code,
                professor = professor,
                creditHours = creditHours,
                colorHex = colorHex
            )
        }
    }

    fun updateSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.updateSubject(subject)
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    fun importSharedPdfsToSubject(
        subjectId: Long,
        uris: List<Uri>,
        category: PdfCategory = PdfCategory.LECTURES,
        onCompleted: (firstFileId: Long?) -> Unit
    ) {
        // يمنع الاستيراد المزدوج: أي استدعاء أثناء تنفيذ استيراد سابق يُتجاهل
        if (uris.isEmpty() || !isImportingSharedFlow.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val result = repository.importPdfUris(subjectId, uris, category)
                if (result.failed.isNotEmpty()) importFailuresFlow.value = result.failed
                onCompleted(result.succeeded.firstOrNull()?.fileId)
            } finally {
                isImportingSharedFlow.value = false
            }
        }
    }

    fun dismissImportFailures() {
        importFailuresFlow.value = emptyList()
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
