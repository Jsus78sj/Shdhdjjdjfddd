package com.abdullah.daftar.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.abdullah.daftar.MainActivity
import com.abdullah.daftar.data.local.dao.PdfFileDao
import com.abdullah.daftar.data.local.dao.SubjectDao
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DaftarAppWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun subjectDao(): SubjectDao
        fun pdfFileDao(): PdfFileDao
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val subjectCount = runCatching { entryPoint.subjectDao().getSubjectCountOnce() }.getOrDefault(0)
        val fileCount = runCatching { entryPoint.pdfFileDao().getFileCountOnce() }.getOrDefault(0)
        val recentFiles = runCatching { entryPoint.pdfFileDao().getRecentFilesOnce(3) }.getOrDefault(emptyList())

        provideContent {
            GlanceTheme {
                WidgetContent(
                    subjectCount = subjectCount,
                    fileCount = fileCount,
                    recentFiles = recentFiles
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        subjectCount: Int,
        fileCount: Int,
        recentFiles: List<PdfFileEntity>
    ) {
        val context = LocalContext.current
        // الضغط على الودجت يفتح آخر ملف قُرئ (أو التطبيق إن لم توجد ملفات)
        val rootAction = recentFiles.firstOrNull()
            ?.let { openFileAction(context, it.id) }
            ?: actionStartActivity<MainActivity>()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(14.dp)
                .clickable(rootAction)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دفتر • متابعة القراءة",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "المواد: $subjectCount | الملفات: $fileCount",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))

            if (recentFiles.isEmpty()) {
                Text(
                    text = "اضغط لفتح التطبيق وإضافة محاضراتك.",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp
                    )
                )
            } else {
                recentFiles.forEach { file ->
                    val current = (file.lastReadPage + 1).coerceIn(1, file.pageCount.coerceAtLeast(1))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable(openFileAction(context, file.id)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• ${file.displayName} (ص $current/${file.pageCount})",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    private fun openFileAction(context: Context, fileId: Long) = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FILE_ID, fileId)
            // data فريد لكل ملف: PendingIntent يتجاهل الـ extras عند المقارنة فكانت ستُدمج الأسطر معاً
            .setData(Uri.parse("daftar://open/$fileId"))
    )

    companion object {
        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    DaftarAppWidget().updateAll(context)
                }
            }
        }
    }
}

class DaftarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaftarAppWidget()
}
