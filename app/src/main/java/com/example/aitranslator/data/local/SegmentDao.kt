package com.example.aitranslator.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aitranslator.domain.model.SegmentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: SegmentEntity): Long

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun getSegmentById(id: Long): SegmentEntity?

    @Query("SELECT * FROM segments WHERE conversationId = :conversationId ORDER BY segmentNumber ASC")
    suspend fun getSegmentsForConversation(conversationId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE conversationId = :conversationId ORDER BY segmentNumber ASC")
    fun observeSegmentsForConversation(conversationId: Long): Flow<List<SegmentEntity>>

    @Query("UPDATE segments SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SegmentStatus, errorMessage: String? = null)

    @Query("UPDATE segments SET originalText = :originalText, translatedText = :translatedText, status = :status, errorMessage = NULL WHERE id = :id")
    suspend fun updateResult(id: Long, originalText: String, translatedText: String, status: SegmentStatus)

    @Query("DELETE FROM segments WHERE id = :id")
    suspend fun deleteSegmentById(id: Long)

    @Query("SELECT * FROM segments WHERE conversationId = :conversationId AND segmentNumber < :currentSegmentNumber AND status = 'COMPLETED' ORDER BY segmentNumber DESC LIMIT :limit")
    suspend fun getRecentCompletedSegments(conversationId: Long, currentSegmentNumber: Int, limit: Int): List<SegmentEntity>

    @Query("SELECT * FROM segments ORDER BY id DESC LIMIT :limit")
    fun observeLatestSegments(limit: Int): Flow<List<SegmentEntity>>
}
