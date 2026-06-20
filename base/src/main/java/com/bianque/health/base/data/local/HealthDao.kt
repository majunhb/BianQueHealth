package com.bianque.health.base.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface HealthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HealthRecordEntity)

    @Query("SELECT * FROM health_records WHERE module_type = :moduleType ORDER BY timestamp DESC")
    suspend fun getRecordsByModule(moduleType: String): List<HealthRecordEntity>

    @Query("SELECT * FROM health_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<HealthRecordEntity>

    @Delete
    suspend fun deleteRecord(record: HealthRecordEntity)

    @Query("SELECT * FROM health_records WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getRecordsSince(since: Long): List<HealthRecordEntity>

    @Query("SELECT * FROM user_profiles LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Upsert
    suspend fun upsertUserProfile(profile: UserProfileEntity)
}