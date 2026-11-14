package com.abdullah.daftar.ui.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.daftar.data.local.entity.SemesterEntity
import com.abdullah.daftar.data.local.entity.SubjectEntity
import com.abdullah.daftar.ui.theme.SubjectColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    viewModel: SubjectsViewModel,
    onSubjectClick: (Long) -> Unit,
    onOpenSearch: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileCountBySubject by viewModel.fileCountBySubject.collectAsStateWithLifecycle()
    val noteCountBySubject by viewModel.noteCountBySubject.collectAsStateWithLifecycle()

    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var subjectToEdit by remember { mutableStateOf<SubjectEntity?>(null) }
    var subjectToDelete by remember { mutableStateOf<SubjectEntity?>(null) }
    var showManageSemestersDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "المواد الدراسية",
                            style = MaterialTheme.typography.titleLarge
                        )
                        uiState.selectedSemester?.let { semester ->
                            val suffix = if (semester.isArchived) " (مؤرشف)" else ""
                            Text(
                                text = semester.name + suffix,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "البحث الشامل"
                        )
                    }
                    TextButton(onClick = { showManageSemestersDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "إدارة الفصول",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الفصول")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSubjectDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة مادة",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SemesterSwitcherRow(
                semesters = uiState.visibleSemesters,
                selectedSemesterId = uiState.selectedSemester?.id,
                onSelectSemester = viewModel::selectSemester,
                onAddSemesterClick = { showManageSemestersDialog = true }
            )

            if (uiState.subjects.isEmpty()) {
                EmptySubjectsPlaceholder(
                    onAddClick = { showAddSubjectDialog = true }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.subjects, key = { it.id }) { subject ->
                        SubjectCard(
                            subject = subject,
                            onClick = { onSubjectClick(subject.id) },
                            onEditClick = { subjectToEdit = subject }
                        )
                    }
                }
            }
        }
    }

    if (showAddSubjectDialog) {
        SubjectFormDialog(
            initialSubject = null,
            onDismiss = { showAddSubjectDialog = false },
            onSave = { name, code, professor, hours, colorHex ->
                viewModel.addSubject(name, code, professor, hours, colorHex)
                showAddSubjectDialog = false
            },
            onDelete = null
        )
    }

    subjectToEdit?.let { subject ->
        SubjectFormDialog(
            initialSubject = subject,
            onDismiss = { subjectToEdit = null },
            onSave = { name, code, professor, hours, colorHex ->
                viewModel.updateSubject(
                    subject.copy(
                        name = name,
                        code = code,
                        professor = professor,
                        creditHours = hours,
                        colorHex = colorHex
                    )
                )
                subjectToEdit = null
            },
            onDelete = {
                subjectToDelete = subject
                subjectToEdit = null
            }
        )
    }

    subjectToDelete?.let { subject ->
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text("حذف المادة") },
            text = {
                val summary = contentSummary(
                    files = fileCountBySubject[subject.id] ?: 0,
                    notes = noteCountBySubject[subject.id] ?: 0
                )
                Text(
                    "سيتم حذف المادة \"${subject.name}\" نهائياً" +
                        (if (summary.isNotEmpty()) " مع $summary" else "") +
                        "، إضافةً إلى الإشارات المرجعية والتظليلات المرتبطة بملفاتها. " +
                        "لا يمكن التراجع عن ذلك."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubject(subject)
                        subjectToDelete = null
                    }
                ) {
                    Text("حذف نهائياً", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showManageSemestersDialog) {
        ManageSemestersDialog(
            semesters = uiState.allSemesters,
            subjectCountBySemester = uiState.allSubjects.groupingBy { it.semesterId }.eachCount(),
            fileCountBySemester = uiState.allSubjects.groupBy { it.semesterId }
                .mapValues { (_, subjects) -> subjects.sumOf { fileCountBySubject[it.id] ?: 0 } },
            noteCountBySemester = uiState.allSubjects.groupBy { it.semesterId }
                .mapValues { (_, subjects) -> subjects.sumOf { noteCountBySubject[it.id] ?: 0 } },
            showArchived = uiState.showArchivedSemesters,
            onToggleShowArchived = viewModel::toggleShowArchivedSemesters,
            onDismiss = { showManageSemestersDialog = false },
            onAddSemester = viewModel::addSemester,
            onRenameSemester = viewModel::updateSemesterName,
            onToggleArchiveSemester = viewModel::toggleSemesterArchive,
            onDeleteSemester = viewModel::deleteSemester
        )
    }
}

@Composable
private fun SemesterSwitcherRow(
    semesters: List<SemesterEntity>,
    selectedSemesterId: Long?,
    onSelectSemester: (Long) -> Unit,
    onAddSemesterClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(semesters, key = { it.id }) { semester ->
            val selected = semester.id == selectedSemesterId
            val labelText = if (semester.isArchived) "${semester.name} (مؤرشف)" else semester.name
            FilterChip(
                selected = selected,
                onClick = { onSelectSemester(semester.id) },
                label = { Text(labelText) },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onAddSemesterClick,
                label = { Text("+ الفصول / الأرشيف") }
            )
        }
    }
}

@Composable
private fun SubjectCard(
    subject: SubjectEntity,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val cardColor = Color(subject.colorHex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(155.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = subject.code.ifBlank { "مادة" },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "تعديل المادة",
                        tint = Color.White
                    )
                }
            }

            Column {
                Text(
                    text = subject.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = subject.professor.ifBlank { "غير محدد" },
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${subject.creditHours} س",
                        color = Color.White.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySubjectsPlaceholder(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = "لا توجد مواد مضافة في هذا الفصل",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "اضغط على زر الإضافة لإدراج موادك الدراسية واستيراد محاضرات PDF والملاحظات الخاصة بها.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAddClick) {
                Text("إضافة مادة الآن")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectFormDialog(
    initialSubject: SubjectEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, professor: String, hours: Int, colorHex: Long) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialSubject?.name ?: "") }
    var code by remember { mutableStateOf(initialSubject?.code ?: "") }
    var professor by remember { mutableStateOf(initialSubject?.professor ?: "") }
    var hoursText by remember { mutableStateOf((initialSubject?.creditHours ?: 3).toString()) }
    var selectedColor by remember {
        mutableLongStateOf(initialSubject?.colorHex ?: SubjectColorPalette.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialSubject == null) "إضافة مادة جديدة" else "تعديل بيانات المادة")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المادة *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("الرمز (مثل CE301)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("الساعات") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(95.dp)
                    )
                }
                OutlinedTextField(
                    value = professor,
                    onValueChange = { professor = it },
                    label = { Text("اسم الدكتور / المحاضر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "لون البطاقة:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SubjectColorPalette.forEach { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hours = hoursText.toIntOrNull() ?: 3
                    onSave(name, code, professor, hours, selectedColor)
                },
                enabled = name.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        }
    )
}

@Composable
private fun ManageSemestersDialog(
    semesters: List<SemesterEntity>,
    subjectCountBySemester: Map<Long, Int>,
    fileCountBySemester: Map<Long, Int>,
    noteCountBySemester: Map<Long, Int>,
    showArchived: Boolean,
    onToggleShowArchived: () -> Unit,
    onDismiss: () -> Unit,
    onAddSemester: (String) -> Unit,
    onRenameSemester: (SemesterEntity, String) -> Unit,
    onToggleArchiveSemester: (SemesterEntity) -> Unit,
    onDeleteSemester: (SemesterEntity) -> Unit
) {
    var newSemesterName by remember { mutableStateOf("") }
    var editingSemester by remember { mutableStateOf<SemesterEntity?>(null) }
    var editedName by remember { mutableStateOf("") }
    var semesterToDelete by remember { mutableStateOf<SemesterEntity?>(null) }

    semesterToDelete?.let { semester ->
        val subjectsCount = subjectCountBySemester[semester.id] ?: 0
        AlertDialog(
            onDismissRequest = { semesterToDelete = null },
            title = { Text("حذف الفصل") },
            text = {
                Text(
                    if (subjectsCount > 0) {
                        val summary = contentSummary(
                            files = fileCountBySemester[semester.id] ?: 0,
                            notes = noteCountBySemester[semester.id] ?: 0
                        )
                        "سيتم حذف الفصل \"${semester.name}\" نهائياً مع مواده ($subjectsCount)" +
                            (if (summary.isNotEmpty()) " و$summary" else "") +
                            "، إضافةً إلى الإشارات المرجعية والتظليلات المرتبطة بالملفات. " +
                            "لا يمكن التراجع عن ذلك."
                    } else {
                        "سيتم حذف الفصل \"${semester.name}\" نهائياً. لا يمكن التراجع عن ذلك."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSemester(semester)
                        semesterToDelete = null
                    }
                ) {
                    Text("حذف نهائياً", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { semesterToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدارة الفصول والأرشفة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newSemesterName,
                        onValueChange = { newSemesterName = it },
                        label = { Text("اسم فصل جديد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newSemesterName.isNotBlank()) {
                                onAddSemester(newSemesterName)
                                newSemesterName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة فصل")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إظهار الفصول المؤرشفة في الشريط",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = showArchived,
                        onCheckedChange = { onToggleShowArchived() }
                    )
                }

                semesters.forEach { semester ->
                    Surface(
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (editingSemester?.id == semester.id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        onRenameSemester(semester, editedName)
                                        editingSemester = null
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "حفظ")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = semester.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (semester.isArchived) {
                                        Text(
                                            text = "فصل مؤرشف",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { onToggleArchiveSemester(semester) }
                                    ) {
                                        Icon(
                                            imageVector = if (semester.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            contentDescription = if (semester.isArchived) "إلغاء الأرشفة" else "أرشفة الفصل"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingSemester = semester
                                            editedName = semester.name
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل"
                                        )
                                    }
                                    if (semesters.size > 1) {
                                        IconButton(
                                            onClick = { semesterToDelete = semester }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("تم")
            }
        }
    )
}

/** ملخص محتوى سيُحذف (مثل "3 ملف PDF و2 ملاحظة")؛ فارغ إن لم يوجد شيء. */
private fun contentSummary(files: Int, notes: Int): String = buildList {
    if (files > 0) add("$files ملف PDF")
    if (notes > 0) add("$notes ملاحظة")
}.joinToString(" و")
