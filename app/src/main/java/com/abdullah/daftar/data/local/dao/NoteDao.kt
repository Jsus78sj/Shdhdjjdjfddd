package com.abdullah.daftar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abdullah.daftar.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM subject_notes WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun getNotesBySubject(subjectId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM subject_notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    // [query] يجب أن يكون مُهرَّباً (\ % _) قبل الاستدعاء
    @Query("SELECT * FROM subject_notes WHERE title LIKE '%' || :query || '%' ESCAPE '\\' OR content LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY updatedAt DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    // يُبقي اسم الملف المعروض في الملاحظات مطابقاً بعد إعادة تسميته
    @Query("UPDATE subject_notes SET linkedFileName = :name WHERE linkedFileId = :fileId")
    suspend fun updateLinkedFileName(fileId: Long, name: String)

    // عند حذف الملف: تبقى الملاحظة لكن يُزال رابطها الميت
    @Query("UPDATE subject_notes SET linkedFileId = NULL, linkedFileName = NULL, linkedPage = NULL WHERE linkedFileId = :fileId")
    suspend fun clearFileLink(fileId: Long)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}
