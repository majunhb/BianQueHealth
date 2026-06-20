package com.bianque.health.engine.data

import com.bianque.health.engine.domain.model.BodyType
import com.bianque.health.engine.domain.model.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationEngine @Inject constructor() {

    suspend fun generate(
        bodyType: BodyType,
        diagnosisData: Map<String, Any>
    ): List<Recommendation> = withContext(Dispatchers.Default) {
        // TODO: Personalized health recommendation generation
        Timber.d("RecommendationEngine: generating recommendations for ${bodyType.displayName}...")
        listOf(
            Recommendation(
                category = "diet",
                content = "饮食清淡，多吃蔬菜水果",
                priority = 1
            ),
            Recommendation(
                category = "exercise",
                content = "适度运动，如散步、太极拳",
                priority = 2
            ),
            Recommendation(
                category = "lifestyle",
                content = "保持规律作息，避免熬夜",
                priority = 1
            ),
            Recommendation(
                category = "emotional",
                content = "保持心情愉悦，避免情绪波动",
                priority = 2
            )
        )
    }
}