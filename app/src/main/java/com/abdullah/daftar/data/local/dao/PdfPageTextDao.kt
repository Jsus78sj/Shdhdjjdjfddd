package com.abdullah.daftar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abdullah.daftar.data.local.entity.PdfPageTextEntity
import kotlinx.coroutines.flow.Flow

data class PdfContentSearchResult(
    val fileId: Long,
    val subjectId: Long,
    val displayName: String,
    val pageIndex: Int,
    val pageText: String
)

@Dao
interface PdfPageTextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageTexts(pages: List<PdfPageTextEntity>)

    @Query("DELETE FROM pdf_page_texts WHERE fileId = :fileId")
    suspend fun clearTextForFile(fileId: Long)

    /** [query] يجب أن تكون مُطبَّعة بـ ArabicTextNormalizer.normalizeQuery ثم مُهرَّبة (\ % _) بهذا الترتيب. */
    @Query(
        """
        SELECT t.fileId, f.subjectId, f.displayName, t.pageIndex, t.pageText
        FROM pdf_page_texts t
        INNER JOIN pdf_files f ON f.id = t.fileId
        WHERE t.normalizedText LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY f.displayName ASC, t.pageIndex ASC
        LIMIT 100
        """
    )
    fun searchInsideAllPdfContents(query: String): Flow<List<PdfContentSearchResult>>

    @Query(
        """
        SELECT * FROM pdf_page_texts
        WHERE fileId = :fileId AND normalizedText LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY pageIndex ASC
        """
    )
    fun searchInsideSinglePdf(fileId: Long, query: String): Flow<List<PdfPageTextEntity>>

    @Query("SELECT COUNT(*) FROM pdf_page_texts")
    fun getIndexedPagesCount(): Flow<Int>
}
