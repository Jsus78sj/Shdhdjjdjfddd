package com.abdullah.daftar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.abdullah.daftar.data.local.dao.BookmarkDao
import com.abdullah.daftar.data.local.dao.HighlightDao
import com.abdullah.daftar.data.local.dao.NoteDao
import com.abdullah.daftar.data.local.dao.PdfFileDao
import com.abdullah.daftar.data.local.dao.PdfPageTextDao
import com.abdullah.daftar.data.local.dao.SemesterDao
import com.abdullah.daftar.data.local.dao.SubjectDao
import com.abdullah.daftar.data.local.entity.BookmarkEntity
import com.abdullah.daftar.data.local.entity.HighlightEntity
import com.abdullah.daftar.data.local.entity.NoteEntity
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import com.abdullah.daftar.data.local.entity.PdfPageTextEntity
import com.abdullah.daftar.data.local.entity.SemesterEntity
import com.abdullah.daftar.data.local.entity.SubjectEntity

@Database(
    entities = [
        SemesterEntity::class,
        SubjectEntity::class,
        PdfFileEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        PdfPageTextEntity::class,
        HighlightEntity::class
    ],
    version = DaftarDatabase.DATABASE_VERSION,
    // تصدير المخطط ضروري لكتابة واختبار الـ Migrations مستقبلاً (مجلد app/schemas)
    exportSchema = true
)
abstract class DaftarDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun subjectDao(): SubjectDao
    abstract fun pdfFileDao(): PdfFileDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun pdfPageTextDao(): PdfPageTextDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        const val DATABASE_NAME = "daftar_db"

        /**
         * عند أي تغيير في الكيانات (Entities):
         * 1) ارفع هذا الرقم بمقدار 1.
         * 2) أضف Migration جديد في DaftarMigrations.ALL.
         * لا تعتمد أبداً على المسح التلقائي للبيانات.
         */
        const val DATABASE_VERSION = 4
    }
}
