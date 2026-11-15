package com.abdullah.daftar

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abdullah.daftar.data.worker.PdfTextExtractionWorker
import com.abdullah.daftar.ui.navigation.DaftarMainScreen
import com.abdullah.daftar.ui.theme.DaftarTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var sharedPdfUris by mutableStateOf<List<Uri>>(emptyList())
    private var fileIdToOpen by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            sharedPdfUris = extractPdfUrisFromIntent(intent)
            fileIdToOpen = extractOpenFileId(intent)
            // فهرسة أي ملفات غير مفهرسة (مثلاً بعد ترحيل القاعدة إلى v4)؛ KEEP يمنع التكرار
            PdfTextExtractionWorker.enqueueAllUnindexed(applicationContext)
        }
        setContent {
            DaftarTheme {
                DaftarMainScreen(
                    incomingSharedPdfUris = sharedPdfUris,
                    onConsumeSharedPdfUris = { sharedPdfUris = emptyList() },
                    fileIdToOpen = fileIdToOpen,
                    onFileOpenHandled = { fileIdToOpen = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newUris = extractPdfUrisFromIntent(intent)
        if (newUris.isNotEmpty()) {
            sharedPdfUris = newUris
        }
        extractOpenFileId(intent)?.let { fileIdToOpen = it }
    }

    /** معرّف الملف المطلوب فتحه (من ضغطة على الودجت)، أو null. */
    private fun extractOpenFileId(intent: Intent?): Long? {
        val raw = intent?.extras?.get(EXTRA_OPEN_FILE_ID)
        val id = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
        return id?.takeIf { it > 0L }
    }

    private fun extractPdfUrisFromIntent(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                listOfNotNull(intent.data)
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.filterNotNull() ?: emptyList()
            }
            else -> emptyList()
        }
    }

    companion object {
        const val EXTRA_OPEN_FILE_ID = "com.abdullah.daftar.OPEN_FILE_ID"
    }
}
