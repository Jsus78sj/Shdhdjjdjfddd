package com.abdullah.daftar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abdullah.daftar.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM pdf_highlights WHERE fileId = :fileId ORDER BY pageIndex ASC, createdAt ASC")
    fun getHighlightsByFile(fileId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT COUNT(*) FROM pdf_highlights")
    fun getTotalHighlightsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM pdf_highlights WHERE fileId = :fileId AND pageIndex = :pageIndex")
    suspend fun clearHighlightsOnPage(fileId: Long, pageIndex: Int)

    @Query(
        """
        DELETE FROM pdf_highlights
        WHERE id = (
            SELECT id FROM pdf_highlights
            WHERE fileId = :fileId AND pageIndex = :pageIndex
            ORDER BY createdAt DESC
            LIMIT 1
        )
        """
    )
    suspend fun undoLastHighlightOnPage(fileId: Long, pageIndex: Int)
}
