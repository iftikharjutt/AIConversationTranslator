package com.example.aitranslator.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineModelDao {
    @Query("SELECT * FROM offline_models ORDER BY modelName ASC")
    fun observeAllModels(): Flow<List<OfflineModelEntity>>

    @Query("SELECT * FROM offline_models WHERE modelId = :modelId LIMIT 1")
    suspend fun getModelById(modelId: String): OfflineModelEntity?

    @Query("SELECT * FROM offline_models WHERE modelId = :modelId LIMIT 1")
    fun observeModelById(modelId: String): Flow<OfflineModelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: OfflineModelEntity)

    @Update
    suspend fun updateModel(model: OfflineModelEntity)

    @Query("DELETE FROM offline_models WHERE modelId = :modelId")
    suspend fun deleteModelById(modelId: String)
}
