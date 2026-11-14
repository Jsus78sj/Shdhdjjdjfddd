package com.abdullah.daftar.ui.navigation

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abdullah.daftar.data.local.entity.SubjectEntity
import com.abdullah.daftar.ui.pdf_viewer.PdfViewerScreen
import com.abdullah.daftar.ui.pdf_viewer.PdfViewerViewModel
import com.abdullah.daftar.ui.search.SearchScreen
import com.abdullah.daftar.ui.search.SearchViewModel
import com.abdullah.daftar.ui.settings.SettingsScreen
import com.abdullah.daftar.ui.settings.SettingsViewModel
import com.abdullah.daftar.ui.subject_detail.SubjectDetailScreen
import com.abdullah.daftar.ui.subject_detail.ImportFailuresDialog
import com.abdullah.daftar.ui.subject_detail.SubjectDetailViewModel
import com.abdullah.daftar.ui.subjects.SubjectsScreen
import com.abdullah.daftar.ui.subjects.SubjectsViewModel

@Composable
fun DaftarMainScreen(
    incomingSharedPdfUris: List<Uri> = emptyList(),
    onConsumeSharedPdfUris: () -> Unit = {},
    fileIdToOpen: Long? = null,
    onFileOpenHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // فتح ملف مطلوب من خارج التطبيق (ضغطة على الودجت): يعمل بعد تركيب NavHost
    LaunchedEffect(fileIdToOpen) {
        fileIdToOpen?.let { id ->
            navController.navigate(Screen.createPdfViewerRoute(id))
            onFileOpenHandled()
        }
    }

    val subjectsViewModel: SubjectsViewModel = hiltViewModel()
    val subjectsState by subjectsViewModel.uiState.collectAsStateWithLifecycle()
    val isImportingShared by subjectsViewModel.isImportingShared.collectAsStateWithLifecycle()
    val importFailures by subjectsViewModel.importFailures.collectAsStateWithLifecycle()

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(text = screen.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Subjects.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = Screen.Subjects.route) {
                SubjectsScreen(
                    viewModel = subjectsViewModel,
                    onSubjectClick = { subjectId ->
                        navController.navigate(Screen.createSubjectDetailRoute(subjectId))
                    },
                    onOpenSearch = {
                        navController.navigate(Screen.SEARCH_ROUTE)
                    }
                )
            }

            composable(route = Screen.SEARCH_ROUTE) {
                val searchViewModel: SearchViewModel = hiltViewModel()
                SearchScreen(
                    viewModel = searchViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSubject = { subjectId ->
                        navController.navigate(Screen.createSubjectDetailRoute(subjectId))
                    },
                    onOpenPdfFile = { fileId, initialPage ->
                        navController.navigate(Screen.createPdfViewerRoute(fileId, initialPage))
                    }
                )
            }

            composable(
                route = Screen.SUBJECT_DETAIL_ROUTE,
                arguments = listOf(navArgument("subjectId") { type = NavType.LongType })
            ) {
                val detailViewModel: SubjectDetailViewModel = hiltViewModel()
                SubjectDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPdfFile = { fileId, initialPage ->
                        navController.navigate(Screen.createPdfViewerRoute(fileId, initialPage))
                    }
                )
            }

            composable(
                route = Screen.PDF_VIEWER_ROUTE,
                arguments = listOf(
                    navArgument("fileId") { type = NavType.LongType },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) {
                val viewerViewModel: PdfViewerViewModel = hiltViewModel()
                PdfViewerScreen(
                    viewModel = viewerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = Screen.Home.route) {
                PlaceholderScreen(title = Screen.Home.title)
            }
            composable(route = Screen.Schedule.route) {
                PlaceholderScreen(title = Screen.Schedule.title)
            }
            composable(route = Screen.Study.route) {
                PlaceholderScreen(title = Screen.Study.title)
            }
            composable(route = Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }

    // تبقى النافذة ظاهرة (بحالة تقدّم) أثناء الاستيراد حتى بعد استهلاك الـ URIs
    if (incomingSharedPdfUris.isNotEmpty() || isImportingShared) {
        IncomingSharedPdfDialog(
            urisCount = incomingSharedPdfUris.size,
            isImporting = isImportingShared,
            subjects = subjectsState.allSubjects,
            onDismiss = onConsumeSharedPdfUris,
            onSelectSubject = { subject ->
                // استهلاك الـ URIs فوراً بعد أول ضغطة؛ والـ ViewModel يتجاهل أي استدعاء أثناء التنفيذ
                val uris = incomingSharedPdfUris
                onConsumeSharedPdfUris()
                subjectsViewModel.importSharedPdfsToSubject(
                    subjectId = subject.id,
                    uris = uris,
                    onCompleted = { firstFileId ->
                        // إن فشلت كل الملفات نبقى في مكاننا وتظهر نافذة الفاشلة
                        if (firstFileId != null) {
                            navController.navigate(Screen.createPdfViewerRoute(firstFileId))
                        }
                    }
                )
            }
        )
    }

    if (importFailures.isNotEmpty()) {
        ImportFailuresDialog(
            failed = importFailures,
            onDismiss = subjectsViewModel::dismissImportFailures
        )
    }
}

@Composable
private fun IncomingSharedPdfDialog(
    urisCount: Int,
    isImporting: Boolean,
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSelectSubject: (SubjectEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = {
            Text(if (isImporting) "جارٍ الحفظ في دفتر…" else "حفظ في دفتر ($urisCount ملف PDF)")
        },
        text = {
            if (isImporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("جارٍ نسخ الملفات، يرجى الانتظار")
                }
            } else if (subjects.isEmpty()) {
                Text("يرجى إضافة مادة دراسية أولاً من شاشة المواد حتى تتمكن من حفظ الملفات بداخلها.")
            } else {
                Column {
                    Text(
                        text = "اختر المادة التي تريد إضافة الملف إليها وفتحه:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(240.dp)
                    ) {
                        items(subjects, key = { it.id }) { subject ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSubject(subject) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = Color(subject.colorHex),
                                            shape = CircleShape,
                                            modifier = Modifier.fillMaxSize()
                                        ) {}
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = subject.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (subject.code.isNotBlank()) {
                                            Text(
                                                text = subject.code,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "شاشة $title",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
