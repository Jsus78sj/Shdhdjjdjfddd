package com.abdullah.daftar.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "الرئيسية", Icons.Default.Home)
    data object Subjects : Screen("subjects", "المواد", Icons.Default.MenuBook)
    data object Schedule : Screen("schedule", "الجدول", Icons.Default.CalendarMonth)
    data object Study : Screen("study", "المذاكرة", Icons.Default.Timer)
    data object Settings : Screen("settings", "الإعدادات", Icons.Default.Settings)

    companion object {
        const val SUBJECT_DETAIL_ROUTE = "subject_detail/{subjectId}"
        const val PDF_VIEWER_ROUTE = "pdf_viewer/{fileId}?page={page}"
        const val SEARCH_ROUTE = "search"

        fun createSubjectDetailRoute(subjectId: Long) = "subject_detail/$subjectId"
        fun createPdfViewerRoute(fileId: Long, initialPage: Int? = null): String {
            return if (initialPage != null && initialPage >= 0) {
                "pdf_viewer/$fileId?page=$initialPage"
            } else {
                "pdf_viewer/$fileId?page=-1"
            }
        }
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Subjects,
    Screen.Schedule,
    Screen.Study,
    Screen.Settings
)
