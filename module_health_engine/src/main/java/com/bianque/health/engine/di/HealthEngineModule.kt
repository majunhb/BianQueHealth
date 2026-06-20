package com.bianque.health.engine.di

import com.bianque.health.engine.data.HealthEngineRepositoryImpl
import com.bianque.health.engine.domain.HealthEngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthEngineModule {

    @Binds
    @Singleton
    abstract fun bindHealthEngineRepository(
        impl: HealthEngineRepositoryImpl
    ): HealthEngineRepository
}