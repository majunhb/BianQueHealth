package com.bianque.health.base.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_records",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["module_type", "timestamp"])
    ]
)
data class HealthRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "module_type")
    val moduleType: String,
    @ColumnInfo(name = "result_json")
    val resultJson: String,
    val timestamp: Long,
    val confidence: Float,
    @ColumnInfo(name = "sync_status")
    val syncStatus: Int
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    @ColumnInfo(name = "height_cm")
    val heightCm: Float,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Float,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)