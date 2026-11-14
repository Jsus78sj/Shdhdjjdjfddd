package com.abdullah.daftar.ui.subject_detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.daftar.data.local.entity.NoteEntity
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.repository.FailedPdfImport
import com.abdullah.daftar.domain.model.PdfCategory
import com.abdullah.daftar.domain.model.PdfSortOption
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    viewModel: SubjectDetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenPdfFile: (fileId: Long, initialPage: Int?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importFailures by viewModel.importFailures.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fileToEdit by remember { mutableStateOf<PdfFileEntity?>(null) }
    var fileToDelete by remember { mutableStateOf<PdfFileEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.subject?.name ?: "تفاصيل المادة",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    if (selectedTabIndex == 0) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "ترتيب الملفات"
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                PdfSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            val prefix = if (uiState.sortOption == option) "✓ " else ""
                                            Text("$prefix${option.arabicLabel}")
                                        },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                ExtendedFloatingActionButton(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "استيراد ملفات PDF"
                        )
                    },
                    text = { Text("استيراد PDF") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { showAddNoteDialog = true },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = "إضافة ملاحظة"
                        )
                    },
                    text = { Text("ملاحظة جديدة") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // بطاقة معلومات المادة العلوية
            uiState.subject?.let { subject ->
                Surface(
                    color = Color(subject.colorHex),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = subject.code.ifBlank { "بدون رمز" },
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${subject.creditHours} ساعات معتمدة",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subject.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "المحاضر: ${subject.professor.ifBlank { "غير محدد" }} • الملفات: ${uiState.allPdfFiles.size} • الملاحظات: ${uiState.notes.size}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // تبويبات: الملفات | الملاحظات
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("الملفات (${uiState.allPdfFiles.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("الملاحظات (${uiState.notes.size})") }
                )
            }

            if (selectedTabIndex == 0) {
                FilesTabContent(
                    uiState = uiState,
                    onSelectCategory = viewModel::selectCategory,
                    onToggleFavorites = viewModel::toggleFavoritesFilter,
                    onOpenPdf = { fileId -> onOpenPdfFile(fileId, null) },
                    onToggleFileFavorite = viewModel::toggleFileFavorite,
                    onEditFile = { fileToEdit = it },
                    onDeleteFile = { fileToDelete = it }
                )
            } else {
                NotesTabContent(
                    notes = uiState.notes,
                    onEditNote = { noteToEdit = it },
                    onDeleteNote = { noteToDelete = it },
                    onOpenLinkedPdfPage = { fileId, pageNumber ->
                        onOpenPdfFile(fileId, (pageNumber - 1).coerceAtLeast(0))
                    }
                )
            }
        }
    }

    // نافذة اختيار تصنيف الملفات عند الاستيراد
    if (pendingImportUris.isNotEmpty()) {
        SelectCategoryForImportDialog(
            urisCount = pendingImportUris.size,
            onDismiss = { pendingImportUris = emptyList() },
            onConfirmCategory = { category ->
                viewModel.importPdfs(pendingImportUris, category)
                pendingImportUris = emptyList()
            }
        )
    }

    // نافذة الملفات التي فشل استيرادها
    if (importFailures.isNotEmpty()) {
        ImportFailuresDialog(
            failed = importFailures,
            onDismiss = viewModel::dismissImportFailures
        )
    }

    // نافذة تعديل اسم وتصنيف الملف
    fileToEdit?.let { pdf ->
        EditPdfFileDialog(
            pdfFile = pdf,
            onDismiss = { fileToEdit = null },
            onSave = { newName, category ->
                viewModel.updateFileNameAndCategory(pdf, newName, category)
                fileToEdit = null
            }
        )
    }

    // نافذة حذف الملف
    fileToDelete?.let { pdf ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("حذف الملف") },
            text = {
                // الملاحظات مرتبطة بالمادة لا بالملف: تبقى بعد الحذف ويُزال رابطها بالملف فقط
                val linkedNotes = uiState.notes.count { it.linkedFileId == pdf.id }
                Text(
                    "سيتم حذف الملف \"${pdf.displayName}\" (${pdf.pageCount} صفحة) نهائياً من التطبيق " +
                        "مع إشاراته المرجعية وتظليلاته ونصه المفهرس للبحث. " +
                        (if (linkedNotes > 0) "ستبقى $linkedNotes ملاحظة مرتبطة به ويُزال ربطها بالملف. " else "") +
                        "لا يمكن التراجع عن ذلك."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePdfFile(pdf)
                        fileToDelete = null
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // نافذة حذف الملاحظة
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("حذف الملاحظة") },
            text = {
                Text(
                    "سيتم حذف الملاحظة" +
                        (if (note.title.isNotBlank()) " \"${note.title}\"" else "") +
                        " نهائياً. لا يمكن التراجع عن ذلك."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note)
                        noteToDelete = null
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // نافذة إضافة ملاحظة جديدة
    if (showAddNoteDialog) {
        NoteFormDialog(
            initialNote = null,
            subjectFiles = uiState.allPdfFiles,
            onDismiss = { showAddNoteDialog = false },
            onSave = { title, content, linkedFile, linkedPage ->
                viewModel.addNote(title, content, linkedFile, linkedPage)
                showAddNoteDialog = false
            }
        )
    }

    // نافذة تعديل ملاحظة
    noteToEdit?.let { note ->
        NoteFormDialog(
            initialNote = note,
            subjectFiles = uiState.allPdfFiles,
            onDismiss = { noteToEdit = null },
            onSave = { title, content, linkedFile, linkedPage ->
                viewModel.updateNote(note, title, content, linkedFile, linkedPage)
                noteToEdit = null
            }
        )
    }
}

@Composable
private fun FilesTabContent(
    uiState: SubjectDetailUiState,
    onSelectCategory: (PdfCategory?) -> Unit,
    onToggleFavorites: () -> Unit,
    onOpenPdf: (Long) -> Unit,
    onToggleFileFavorite: (PdfFileEntity) -> Unit,
    onEditFile: (PdfFileEntity) -> Unit,
    onDeleteFile: (PdfFileEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // شريط التصنيفات والمفضلة
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedCategory == null && !uiState.showFavoritesOnly,
                    onClick = {
                        onSelectCategory(null)
                        if (uiState.showFavoritesOnly) onToggleFavorites()
                    },
                    label = { Text("الكل") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.showFavoritesOnly,
                    onClick = onToggleFavorites,
                    label = { Text("المفضلة") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            items(PdfCategory.entries) { category ->
                FilterChip(
                    selected = uiState.selectedCategory == category,
                    onClick = {
                        onSelectCategory(if (uiState.selectedCategory == category) null else category)
                    },
                    label = { Text(category.arabicLabel) }
                )
            }
        }

        if (uiState.isImporting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("جاري نسخ وتصنيف ملفات PDF...")
            }
        }

        if (uiState.filteredPdfFiles.isEmpty() && !uiState.isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    )
                    Text(
                        text = "لا توجد ملفات مطابقة للفلتر الحالي",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "اضغط على (استيراد PDF) لإضافة محاضرات أو ملخصات أو اختبارات سابقة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.filteredPdfFiles, key = { it.id }) { pdfFile ->
                    PdfFileItemCard(
                        pdfFile = pdfFile,
                        onClick = { onOpenPdf(pdfFile.id) },
                        onToggleFavorite = { onToggleFileFavorite(pdfFile) },
                        onEditClick = { onEditFile(pdfFile) },
                        onDeleteClick = { onDeleteFile(pdfFile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesTabContent(
    notes: List<NoteEntity>,
    onEditNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onOpenLinkedPdfPage: (fileId: Long, pageNumber: Int) -> Unit
) {
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
                Text(
                    text = "لا توجد ملاحظات مسجلة في هذه المادة",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "أضف ملاحظاتك الدراسية واربطها بصفحة محددة من أي ملف PDF للرجوع إليها فوراً.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(notes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditNote(note) },
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteNote(note) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف الملاحظة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (note.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (note.linkedFileId != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val pageNum = note.linkedPage ?: 1
                            AssistChip(
                                onClick = {
                                    onOpenLinkedPdfPage(note.linkedFileId, pageNum)
                                },
                                label = {
                                    Text("فتح ${note.linkedFileName ?: "الملف"} - صفحة $pageNum")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfFileItemCard(
    pdfFile: PdfFileEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val totalPages = pdfFile.pageCount.coerceAtLeast(1)
    val currentPageDisplay = (pdfFile.lastReadPage + 1).coerceIn(1, totalPages)
    val progress = currentPageDisplay.toFloat() / totalPages.toFloat()
    val categoryLabel = PdfCategory.fromName(pdfFile.category).arabicLabel

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = pdfFile.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = categoryLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${pdfFile.pageCount} صفحة • ${formatFileSize(pdfFile.sizeBytes)} • آخر قراءة: ص $currentPageDisplay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (pdfFile.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "المفضلة",
                            tint = if (pdfFile.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل"
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (pdfFile.lastReadPage > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectCategoryForImportDialog(
    urisCount: Int,
    onDismiss: () -> Unit,
    onConfirmCategory: (PdfCategory) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(PdfCategory.LECTURES) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصنيف الملفات المستوردة ($urisCount)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر التصنيف المناسب للملفات:")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.arabicLabel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmCategory(selectedCategory) }) {
                Text("استيراد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPdfFileDialog(
    pdfFile: PdfFileEntity,
    onDismiss: () -> Unit,
    onSave: (String, PdfCategory) -> Unit
) {
    var newName by remember(pdfFile) { mutableStateOf(pdfFile.displayName) }
    var selectedCategory by remember(pdfFile) {
        mutableStateOf(PdfCategory.fromName(pdfFile.category))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل اسم وتصنيف الملف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("اسم الملف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("التصنيف:")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.arabicLabel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newName, selectedCategory) },
                enabled = newName.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteFormDialog(
    initialNote: NoteEntity?,
    subjectFiles: List<PdfFileEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, linkedFile: PdfFileEntity?, linkedPage: Int?) -> Unit
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var selectedFile by remember {
        mutableStateOf(subjectFiles.find { it.id == initialNote?.linkedFileId })
    }
    var pageText by remember {
        mutableStateOf(initialNote?.linkedPage?.toString() ?: "1")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialNote == null) "إضافة ملاحظة" else "تعديل الملاحظة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الملاحظة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("محتوى الملاحظة") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                if (subjectFiles.isNotEmpty()) {
                    Text(
                        text = "ربط بملف PDF (اختياري):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedFile == null,
                            onClick = { selectedFile = null },
                            label = { Text("بدون ربط") }
                        )
                        subjectFiles.forEach { file ->
                            FilterChip(
                                selected = selectedFile?.id == file.id,
                                onClick = { selectedFile = file },
                                label = {
                                    Text(
                                        text = file.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                    if (selectedFile != null) {
                        OutlinedTextField(
                            value = pageText,
                            onValueChange = { pageText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("رقم الصفحة (1 - ${selectedFile?.pageCount ?: 1})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxPage = selectedFile?.pageCount?.coerceAtLeast(1) ?: 1
                    val pageNum = if (selectedFile != null) {
                        (pageText.toIntOrNull() ?: 1).coerceIn(1, maxPage)
                    } else null
                    onSave(title, content, selectedFile, pageNum)
                },
                enabled = title.isNotBlank() || content.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb)
    }
}

/** يعرض أسماء الملفات التي تعذّر استيرادها وسبب كل منها (يُستخدم أيضاً من نافذة المشاركة). */
@Composable
fun ImportFailuresDialog(
    failed: List<FailedPdfImport>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعذّر استيراد ${failed.size} ملف") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                failed.forEach { item ->
                    Column {
                        Text(text = item.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = item.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("حسناً") }
        }
    )
}
