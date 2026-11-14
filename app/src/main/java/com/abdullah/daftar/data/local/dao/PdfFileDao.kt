package com.abdullah.daftar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abdullah.daftar.data.local.entity.PdfFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfFileDao {

    @Query("SELECT * FROM pdf_files WHERE subjectId = :subjectId ORDER BY createdAt DESC")
    fun getFilesBySubject(subjectId: Long): Flow<List<PdfFileEntity>>

    @Query("SELECT * FROM pdf_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<PdfFileEntity>>

    @Query("SELECT * FROM pdf_files")
    suspend fun getAllFilesOnce(): List<PdfFileEntity>

    // الملفات التي فشلت فهرستها (extractionError) لا تُعاد تلقائياً؛ تُعاد فقط بطلب المستخدم
    @Query("SELECT * FROM pdf_files WHERE isTextExtracted = 0 AND extractionError IS NULL")
    suspend fun getUnindexedFilesOnce(): List<PdfFileEntity>

    @Query("SELECT * FROM pdf_files ORDER BY COALESCE(lastOpenedAt, createdAt) DESC LIMIT :limit")
    suspend fun getRecentFilesOnce(limit: Int = 3): List<PdfFileEntity>

    @Query("SELECT COUNT(*) FROM pdf_files")
    suspend fun getFileCountOnce(): Int

    @Query("SELECT * FROM pdf_files WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteFiles(): Flow<List<PdfFileEntity>>

    // [query] يجب أن يكون مُهرَّباً (\ % _) قبل الاستدعاء
    @Query("SELECT * FROM pdf_files WHERE displayName LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY isFavorite DESC, createdAt DESC")
    fun searchFiles(query: String): Flow<List<PdfFileEntity>>

    @Query("SELECT * FROM pdf_files WHERE id = :fileId LIMIT 1")
    fun getFileById(fileId: Long): Flow<PdfFileEntity?>

    @Query("SELECT * FROM pdf_files WHERE id = :fileId LIMIT 1")
    suspend fun getFileByIdOnce(fileId: Long): PdfFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfFile(pdfFile: PdfFileEntity): Long

    @Update
    suspend fun updatePdfFile(pdfFile: PdfFileEntity)

    @Query("UPDATE pdf_files SET displayName = :newName, category = :category WHERE id = :fileId")
    suspend fun updateFileNameAndCategory(fileId: Long, newName: String, category: String)

    @Query("UPDATE pdf_files SET isFavorite = :isFavorite WHERE id = :fileId")
    suspend fun setFileFavorite(fileId: Long, isFavorite: Boolean)

    @Query("UPDATE pdf_files SET isTextExtracted = :extracted WHERE id = :fileId")
    suspend fun setFileTextExtracted(fileId: Long, extracted: Boolean)

    @Query("UPDATE pdf_files SET isTextExtracted = :extracted, extractionError = :error WHERE id = :fileId")
    suspend fun setTextExtractionResult(fileId: Long, extracted: Boolean, error: String?)

    @Query("UPDATE pdf_files SET internalPath = :newPath WHERE id = :fileId")
    suspend fun updateInternalPath(fileId: Long, newPath: String)

    @Query("UPDATE pdf_files SET lastReadPage = :lastPage, pageCount = :pageCount, lastOpenedAt = :openedAt WHERE id = :fileId")
    suspend fun updateReadingProgress(
        fileId: Long,
        lastPage: Int,
        pageCount: Int,
        openedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deletePdfFile(pdfFile: PdfFileEntity)
}
