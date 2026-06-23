package com.bianque.health.bp.di

import com.bianque.health.bp.data.BloodPressureRepositoryImpl
import com.bianque.health.bp.domain.BloodPressureRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BloodPressureModule {

    @Binds
    @Singleton
    abstract fun bindBloodPressureRepository(
        impl: BloodPressureRepositoryImpl
    ): BloodPressureRepository
}