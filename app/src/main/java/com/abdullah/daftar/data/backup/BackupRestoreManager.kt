package com.abdullah.daftar.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import androidx.work.WorkManager
import com.abdullah.daftar.data.local.DaftarDatabase
import com.abdullah.daftar.data.local.DaftarMigrations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * فشل في الاستعادة.
 * [requiresRestart] = true يعني أن قاعدة البيانات أُغلقت قبل حدوث الخطأ،
 * فيجب إعادة تشغيل التطبيق (والبيانات القديمة أُعيدت إلى مكانها تلقائياً).
 */
class RestoreFailedException(
    message: String,
    val requiresRestart: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DaftarDatabase
) {

    private val pdfsDir: File get() = File(context.filesDir, PDFS_DIR_NAME)
    private val liveDbFile: File get() = context.getDatabasePath(DaftarDatabase.DATABASE_NAME)

    /**
     * تصدير قاعدة البيانات وجميع ملفات PDF الداخلية إلى ملف ZIP واحد
     */
    suspend fun exportBackupToZip(destinationUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            // تفريغ سجلات WAL إلى ملف قاعدة البيانات الأساسي لضمان شمول جميع التعديلات الحديثة
            runCatching { checkpointLiveDatabase("FULL") }

            val dbFile = liveDbFile
            var exportedEntriesCount = 0

            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: error("تعذر إنشاء ملف النسخة الاحتياطية")

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                // 1. إضافة ملف قاعدة البيانات
                if (dbFile.exists()) {
                    addFileToZip(zos, dbFile, "$ZIP_DB_DIR/${DaftarDatabase.DATABASE_NAME}")
                    exportedEntriesCount++
                }

                // 2. إضافة جميع ملفات PDF داخل مجلد pdfs
                val root = pdfsDir
                if (root.exists()) {
                    root.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val relativePath = file.relativeTo(context.filesDir).invariantSeparatorsPath
                            addFileToZip(zos, file, relativePath)
                            exportedEntriesCount++
                        }
                }
            }
            exportedEntriesCount
        }
    }

    /**
     * استعادة قاعدة البيانات وملفات PDF من ملف النسخة الاحتياطية ZIP.
     *
     * 1) فك الضغط إلى مجلد مؤقت والتحقق من القاعدة (سلامة، إصدار، توافق) دون المساس بالبيانات الحالية.
     * 2) تصحيح مسارات الملفات داخل القاعدة المؤقتة.
     * 3) إيقاف أعمال الخلفية ثم إغلاق Room.
     * 4) نقل البيانات الحالية إلى مجلد تراجع ثم وضع المستعادة مكانها (مع تراجع تلقائي عند الفشل).
     *
     * عند النجاح يجب استدعاء [restartApp]: Room أُغلق ولا يمكن استخدامه بعد الآن في هذه العملية.
     */
    suspend fun restoreBackupFromZip(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.filesDir, STAGING_DIR_NAME)
        val rollbackDir = File(context.filesDir, ROLLBACK_DIR_NAME)
        var databaseClosed = false

        try {
            stagingDir.deleteRecursively()
            rollbackDir.deleteRecursively()
            stagingDir.mkdirs()

            val extractedCount = extractZipToStaging(sourceUri, stagingDir)

            val stagedDbFile = File(stagingDir, "$ZIP_DB_DIR/${DaftarDatabase.DATABASE_NAME}")
            if (!stagedDbFile.exists()) {
                throw RestoreFailedException("ملف ZIP المختار لا يحتوي على قاعدة بيانات تطبيق دفتر")
            }

            validateAndRepairStagedDatabase(stagedDbFile)

            // من هنا لا نسمح بالإلغاء: انقطاع العملية في منتصف الاستبدال يترك بيانات نصف مستعادة
            withContext(NonCancellable) {
                runCatching { WorkManager.getInstance(context).cancelAllWork() }
                // الإغلاق يفرّغ WAL ويحرّر الملفات؛ بعده لا يجوز لأي جزء استخدام القاعدة
                database.close()
                databaseClosed = true
                swapInRestoredData(stagingDir, rollbackDir, stagedDbFile)
            }

            stagingDir.deleteRecursively()
            Result.success(extractedCount)
        } catch (e: CancellationException) {
            stagingDir.deleteRecursively()
            throw e
        } catch (e: Throwable) {
            stagingDir.deleteRecursively()
            val failure = when {
                e is RestoreFailedException && (e.requiresRestart || !databaseClosed) -> e
                e is RestoreFailedException -> RestoreFailedException(e.message.orEmpty(), true, e)
                else -> RestoreFailedException(
                    message = e.message ?: "خطأ غير متوقع",
                    requiresRestart = databaseClosed,
                    cause = e
                )
            }
            Result.failure(failure)
        }
    }

    /**
     * يعيد تشغيل العملية بالكامل بحيث يفتح Room ملف القاعدة المستعادة من جديد.
     * يمر عبر [RestartActivity] التي تعمل في عملية منفصلة (:restart) لتقتل العملية الرئيسية
     * ثم تطلق التطبيق؛ هذا يمنع أن تُنشأ الواجهة الجديدة داخل العملية القديمة ذات القاعدة المغلقة.
     */
    fun restartApp() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launchIntent == null) {
            Process.killProcess(Process.myPid())
            return
        }
        val trampoline = Intent(context, RestartActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(RestartActivity.EXTRA_NEXT_INTENT, launchIntent)
            putExtra(RestartActivity.EXTRA_MAIN_PID, Process.myPid())
        }
        context.startActivity(trampoline)
    }

    // ───────────────────────── التصدير ─────────────────────────

    /**
     * ملاحظة: Cursor في أندرويد كسول، فلا يُنفَّذ PRAGMA إلا عند قراءة أول صف (moveToFirst).
     */
    private fun checkpointLiveDatabase(mode: String) {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint($mode)")
            .use { it.moveToFirst() }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                bis.copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    // ───────────────────────── الاستعادة ─────────────────────────

    private fun extractZipToStaging(sourceUri: Uri, stagingDir: File): Int {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw RestoreFailedException("تعذر قراءة ملف النسخة الاحتياطية")

        val stagingRoot = stagingDir.canonicalPath + File.separator
        var count = 0
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val wanted = !entry.isDirectory &&
                    (name.startsWith("$ZIP_DB_DIR/") || name.startsWith("$PDFS_DIR_NAME/"))
                if (wanted) {
                    val outFile = File(stagingDir, name)
                    // حماية من Zip Slip
                    if (!outFile.canonicalPath.startsWith(stagingRoot)) {
                        throw RestoreFailedException("مسار غير آمن داخل ملف النسخة الاحتياطية")
                    }
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    /**
     * يفحص القاعدة المؤقتة ويصحح مسارات ملفات PDF فيها (تعمل على القاعدة المؤقتة وليس الحية).
     */
    private fun validateAndRepairStagedDatabase(stagedDbFile: File) {
        val db = try {
            SQLiteDatabase.openDatabase(stagedDbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            throw RestoreFailedException("قاعدة البيانات داخل النسخة تالفة أو غير مقروءة", cause = e)
        }

        try {
            val integrity = db.rawQuery("PRAGMA integrity_check", null)
                .use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (integrity != "ok") {
                throw RestoreFailedException("فشل فحص سلامة قاعدة البيانات داخل النسخة الاحتياطية")
            }

            val backupVersion = db.version
            if (backupVersion > DaftarDatabase.DATABASE_VERSION) {
                throw RestoreFailedException(
                    "النسخة الاحتياطية أحدث (قاعدة v$backupVersion) من هذا الإصدار من التطبيق (v${DaftarDatabase.DATABASE_VERSION}). حدّث التطبيق أولاً."
                )
            }
            if (backupVersion < DaftarMigrations.MIN_SUPPORTED_VERSION) {
                throw RestoreFailedException(
                    "النسخة الاحتياطية قديمة جداً (قاعدة v$backupVersion) ولا يمكن ترحيلها."
                )
            }

            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            val missing = BASELINE_TABLES - tables
            if (missing.isNotEmpty()) {
                throw RestoreFailedException("قاعدة البيانات داخل النسخة ناقصة (جداول مفقودة: ${missing.joinToString()})")
            }

            // عند تساوي الإصدار يجب أن يتطابق مخطط Room، وإلا سيتوقف التطبيق عند أول فتح
            if (backupVersion == DaftarDatabase.DATABASE_VERSION) {
                val backupHash = db.rawQuery(IDENTITY_HASH_QUERY, null)
                    .use { c -> if (c.moveToFirst()) c.getString(0) else null }
                val currentHash = database.openHelper.readableDatabase.query(IDENTITY_HASH_QUERY)
                    .use { c -> if (c.moveToFirst()) c.getString(0) else null }
                if (backupHash == null || (currentHash != null && backupHash != currentHash)) {
                    throw RestoreFailedException("مخطط قاعدة البيانات في النسخة لا يطابق هذا الإصدار من التطبيق")
                }
            }

            repairPdfPaths(db)

            // دمج أي سجلات WAL داخل الملف الرئيسي قبل نقله
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        } finally {
            db.close()
        }

        val stagedWal = File(stagedDbFile.path + "-wal")
        val stagedShm = File(stagedDbFile.path + "-shm")
        if (stagedWal.exists() && stagedWal.length() > 0L) {
            throw RestoreFailedException("تعذر دمج سجل التغييرات في قاعدة البيانات المستعادة")
        }
        stagedWal.delete()
        stagedShm.delete()
    }

    /** يجعل مسارات PDF تشير إلى مجلد التطبيق الحالي (يتغير اسم المسار بين الأجهزة). */
    private fun repairPdfPaths(db: SQLiteDatabase) {
        val marker = "/$PDFS_DIR_NAME/"
        val base = pdfsDir
        val updates = mutableListOf<Pair<Long, String>>()

        db.rawQuery("SELECT id, internalPath FROM pdf_files", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val path = c.getString(1) ?: continue
                val idx = path.indexOf(marker)
                if (idx >= 0) {
                    val corrected = File(base, path.substring(idx + marker.length)).absolutePath
                    if (corrected != path) updates += id to corrected
                }
            }
        }

        if (updates.isEmpty()) return
        db.beginTransaction()
        try {
            for ((id, corrected) in updates) {
                db.execSQL(
                    "UPDATE pdf_files SET internalPath = ? WHERE id = ?",
                    arrayOf<Any>(corrected, id)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * ينقل البيانات الحالية إلى [rollbackDir] ثم يضع المستعادة مكانها.
     * عند أي خطأ يعيد البيانات القديمة. النقل بالـ rename فوري وذري داخل نفس القسم.
     */
    private fun swapInRestoredData(stagingDir: File, rollbackDir: File, stagedDbFile: File) {
        val dbDir = liveDbFile.parentFile
            ?: throw RestoreFailedException("تعذر تحديد مجلد قاعدة البيانات", requiresRestart = true)
        val dbName = liveDbFile.name
        val rollbackDbDir = File(rollbackDir, "db")
        val rollbackPdfsDir = File(rollbackDir, PDFS_DIR_NAME)

        val movedDbNames = mutableListOf<String>()
        var pdfsMoved = false

        try {
            rollbackDbDir.mkdirs()

            // 1) إزاحة البيانات الحالية (القاعدة + WAL/SHM القديمة يجب ألا تبقى أبداً بجانب القاعدة الجديدة)
            for (suffix in DB_FILE_SUFFIXES) {
                val file = File(dbDir, dbName + suffix)
                if (file.exists()) {
                    moveFile(file, File(rollbackDbDir, file.name))
                    movedDbNames += file.name
                }
            }
            if (pdfsDir.exists()) {
                moveDirectory(pdfsDir, rollbackPdfsDir)
                pdfsMoved = true
            }

            // 2) وضع البيانات المستعادة
            moveFile(stagedDbFile, liveDbFile)
            val stagedPdfs = File(stagingDir, PDFS_DIR_NAME)
            if (stagedPdfs.exists()) moveDirectory(stagedPdfs, pdfsDir) else pdfsDir.mkdirs()
        } catch (e: Throwable) {
            val rolledBack = runCatching {
                for (suffix in DB_FILE_SUFFIXES) File(dbDir, dbName + suffix).delete()
                pdfsDir.deleteRecursively()
                for (name in movedDbNames) moveFile(File(rollbackDbDir, name), File(dbDir, name))
                if (pdfsMoved) moveDirectory(rollbackPdfsDir, pdfsDir)
            }.isSuccess

            if (rolledBack) rollbackDir.deleteRecursively()
            throw RestoreFailedException(
                message = if (rolledBack) {
                    "تعذر استبدال البيانات، وأُعيدت بياناتك السابقة كما كانت"
                } else {
                    "تعذر استبدال البيانات ولم ينجح التراجع التلقائي. بياناتك السابقة محفوظة في مجلد $ROLLBACK_DIR_NAME داخل ملفات التطبيق"
                },
                requiresRestart = true,
                cause = e
            )
        }

        // نجحت الاستعادة: احذف النسخة القديمة (قد يستغرق ثوانٍ إن كانت ملفات PDF كثيرة)
        rollbackDir.deleteRecursively()
    }

    private fun moveFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
    }

    private companion object {
        const val PDFS_DIR_NAME = "pdfs"
        const val ZIP_DB_DIR = "database"
        const val STAGING_DIR_NAME = "restore_staging"
        const val ROLLBACK_DIR_NAME = "restore_rollback"
        val DB_FILE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

        /** جداول خط الأساس (النسخة 3). لا تضف هنا جداول جديدة، فالنسخ الأقدم لن تحتويها. */
        val BASELINE_TABLES = setOf(
            "semesters", "subjects", "pdf_files", "bookmarks",
            "subject_notes", "pdf_page_texts", "pdf_highlights", "room_master_table"
        )

        const val IDENTITY_HASH_QUERY = "SELECT identity_hash FROM room_master_table WHERE id = 42"
    }
}
