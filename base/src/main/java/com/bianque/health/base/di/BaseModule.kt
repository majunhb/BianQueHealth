package com.bianque.health.base.di

import android.content.Context
import androidx.room.Room
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BaseModule {

    @Provides
    @Singleton
    fun provideHealthDatabase(@ApplicationContext context: Context): HealthDatabase =
        Room.databaseBuilder(context, HealthDatabase::class.java, "bianque_health.db").build()

    @Provides
    fun provideHealthDao(db: HealthDatabase): HealthDao = db.healthDao()
}