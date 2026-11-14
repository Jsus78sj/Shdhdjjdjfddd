package com.abdullah.daftar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abdullah.daftar.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subjects WHERE semesterId = :semesterId ORDER BY createdAt DESC")
    fun getSubjectsBySemester(semesterId: Long): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY createdAt DESC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun getSubjectCountOnce(): Int

    @Query("SELECT id FROM subjects WHERE semesterId = :semesterId")
    suspend fun getSubjectIdsBySemesterOnce(semesterId: Long): List<Long>

    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    fun getSubjectById(subjectId: Long): Flow<SubjectEntity?>

    // [query] يجب أن يكون مُهرَّباً (\ % _) قبل الاستدعاء
    @Query("SELECT * FROM subjects WHERE name LIKE '%' || :query || '%' ESCAPE '\\' OR code LIKE '%' || :query || '%' ESCAPE '\\' OR professor LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY createdAt DESC")
    fun searchSubjects(query: String): Flow<List<SubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)
}
