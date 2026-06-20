package com.bianque.health.base.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HealthRecordEntity::class, UserProfileEntity::class],
    version = 1
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
}