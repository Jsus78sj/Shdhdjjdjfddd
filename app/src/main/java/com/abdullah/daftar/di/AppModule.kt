package com.abdullah.daftar.di

import android.content.Context
import androidx.room.Room
import com.abdullah.daftar.data.local.DaftarDatabase
import com.abdullah.daftar.data.local.DaftarMigrations
import com.abdullah.daftar.data.local.dao.BookmarkDao
import com.abdullah.daftar.data.local.dao.HighlightDao
import com.abdullah.daftar.data.local.dao.NoteDao
import com.abdullah.daftar.data.local.dao.PdfFileDao
import com.abdullah.daftar.data.local.dao.PdfPageTextDao
import com.abdullah.daftar.data.local.dao.SemesterDao
import com.abdullah.daftar.data.local.dao.SubjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDaftarDatabase(
        @ApplicationContext context: Context
    ): DaftarDatabase {
        return Room.databaseBuilder(
            context,
            DaftarDatabase::class.java,
            DaftarDatabase.DATABASE_NAME
        )
            .addMigrations(*DaftarMigrations.ALL)
            // مسموح فقط لنسخ التطوير القديمة (1 و2) التي لا مخطط لها.
            // النسخة 3 وما بعدها يجب أن تُرحَّل عبر Migrations ولا تُمسح أبداً.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides
    fun provideSemesterDao(database: DaftarDatabase): SemesterDao = database.semesterDao()

    @Provides
    fun provideSubjectDao(database: DaftarDatabase): SubjectDao = database.subjectDao()

    @Provides
    fun providePdfFileDao(database: DaftarDatabase): PdfFileDao = database.pdfFileDao()

    @Provides
    fun provideBookmarkDao(database: DaftarDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideNoteDao(database: DaftarDatabase): NoteDao = database.noteDao()

    @Provides
    fun providePdfPageTextDao(database: DaftarDatabase): PdfPageTextDao = database.pdfPageTextDao()

    @Provides
    fun provideHighlightDao(database: DaftarDatabase): HighlightDao = database.highlightDao()
}
