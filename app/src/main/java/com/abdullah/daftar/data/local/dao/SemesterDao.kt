package com.abdullah.daftar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.abdullah.daftar.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {

    @Query("SELECT * FROM semesters ORDER BY isArchived ASC, isCurrent DESC, createdAt DESC")
    fun getAllSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE isArchived = 0 ORDER BY isCurrent DESC, createdAt DESC")
    fun getActiveSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentSemester(): Flow<SemesterEntity?>

    @Query("SELECT COUNT(*) FROM semesters")
    suspend fun getSemesterCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(semester: SemesterEntity): Long

    @Update
    suspend fun updateSemester(semester: SemesterEntity)

    @Delete
    suspend fun deleteSemester(semester: SemesterEntity)

    @Query("UPDATE semesters SET isCurrent = 0")
    suspend fun clearCurrentSemesters()

    @Query("UPDATE semesters SET isCurrent = 1 WHERE id = :semesterId")
    suspend fun markSemesterAsCurrent(semesterId: Long)

    @Query("UPDATE semesters SET isArchived = :archived, isCurrent = CASE WHEN :archived = 1 THEN 0 ELSE isCurrent END WHERE id = :semesterId")
    suspend fun setSemesterArchived(semesterId: Long, archived: Boolean)

    @Transaction
    suspend fun setCurrentSemester(semesterId: Long) {
        clearCurrentSemesters()
        markSemesterAsCurrent(semesterId)
    }
}
