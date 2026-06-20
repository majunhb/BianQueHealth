package com.bianque.health.face.di

import com.bianque.health.face.data.ColorAnalyzer
import com.bianque.health.face.data.FaceMeshDetector
import com.bianque.health.face.domain.FaceDiagnosisRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FaceModule {
    @Provides @Singleton
    fun provideFaceMeshDetector(colorAnalyzer: ColorAnalyzer): FaceMeshDetector =
        FaceMeshDetector(colorAnalyzer)
}