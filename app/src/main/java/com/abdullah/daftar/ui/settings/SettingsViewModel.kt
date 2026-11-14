package com.abdullah.daftar.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.daftar.data.backup.BackupRestoreManager
import com.abdullah.daftar.data.backup.RestoreFailedException
import com.abdullah.daftar.data.repository.DaftarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val totalSemesters: Int = 0,
    val totalSubjects: Int = 0,
    val totalFiles: Int = 0,
    val indexedPagesCount: Int = 0,
    val totalHighlightsCount: Int = 0,
    val totalNotes: Int = 0,
    val totalStorageBytes: Long = 0L,
    val isWorking: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DaftarRepository,
    private val backupRestoreManager: BackupRestoreManager
) : ViewModel() {

    private val isWorkingFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getAllSubjects(),
        repository.getAllFiles(),
        repository.getIndexedPagesCount(),
        repository.getTotalHighlightsCount(),
        combine(
            isWorkingFlow,
            statusMessageFlow,
            combine(repository.getAllSemesters(), repository.getAllNotes()) { semesters, notes ->
                semesters.size to notes.size
            }
        ) { w, m, counts -> Triple(w, m, counts) }
    ) { subjects, files, indexedPages, highlights, (working, msg, counts) ->
        SettingsUiState(
            totalSemesters = counts.first,
            totalNotes = counts.second,
            totalSubjects = subjects.size,
            totalFiles = files.size,
            indexedPagesCount = indexedPages,
            totalHighlightsCount = highlights,
            totalStorageBytes = files.sumOf { it.sizeBytes },
            isWorking = working,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun exportBackup(destinationUri: Uri) {
        viewModelScope.launch {
            isWorkingFlow.value = true
            statusMessageFlow.value = "جاري إنشاء ملف النسخة الاحتياطية ZIP..."
            val result = backupRestoreManager.exportBackupToZip(destinationUri)
            isWorkingFlow.value = false
            statusMessageFlow.value = result.fold(
                onSuccess = { count -> "تم تصدير النسخة الاحتياطية بنجاح ($count عنصر)." },
                onFailure = { err -> "فشل التصدير: ${err.message}" }
            )
        }
    }

    fun restoreBackup(sourceUri: Uri) {
        if (isWorkingFlow.value) return
        viewModelScope.launch {
            isWorkingFlow.value = true
            statusMessageFlow.value = "جاري استعادة البيانات والملفات من النسخة الاحتياطية..."
            val result = backupRestoreManager.restoreBackupFromZip(sourceUri)

            if (result.isSuccess) {
                // Room أُغلق: لا بد من إعادة تشغيل العملية ليفتح القاعدة المستعادة.
                // نُبقي isWorking = true لتبقى الأزرار معطلة حتى يُغلق التطبيق.
                statusMessageFlow.value =
                    "تمت استعادة النسخة الاحتياطية (${result.getOrDefault(0)} ملف). جاري إعادة تشغيل التطبيق..."
                backupRestoreManager.restartApp()
            } else {
                val error = result.exceptionOrNull()
                val needsRestart = (error as? RestoreFailedException)?.requiresRestart == true
                if (needsRestart) {
                    statusMessageFlow.value = "فشلت الاستعادة: ${error?.message}. سيُعاد تشغيل التطبيق."
                    backupRestoreManager.restartApp()
                } else {
                    isWorkingFlow.value = false
                    statusMessageFlow.value = "فشلت الاستعادة: ${error?.message}"
                }
            }
        }
    }

    fun reindexAllPdfTexts() {
        repository.triggerTextExtractionForAllUnindexed()
        statusMessageFlow.value = "تمت جدولة فهرسة نصوص ملفات PDF في الخلفية (WorkManager)."
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }
}
