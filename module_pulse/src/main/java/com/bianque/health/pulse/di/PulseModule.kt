package com.bianque.health.pulse.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PulseModule {
    // PulseSignalProcessor, RppGather, RppGProcessor, BloodPressureEstimator
    // are all @Singleton @Inject or object, no explicit binding needed.
}