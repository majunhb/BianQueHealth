package com.bianque.health.di

import android.content.Context
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
object AppModule {

    @Provides
    @Singleton
    fun provideHealthDatabase(@ApplicationContext context: Context): HealthDatabase {
        return HealthDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideHealthDao(database: HealthDatabase): HealthDao {
        return database.healthDao()
    }
}