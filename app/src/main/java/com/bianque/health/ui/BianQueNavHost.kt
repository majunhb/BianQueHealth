package com.bianque.health.ui

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bianque.health.base.security.PrivacyManager
import com.bianque.health.ui.screens.*

@Composable
fun BianQueNavHost() {
    val navController = rememberNavController()

    // 观察隐私同意状态
    val consentGranted by PrivacyManager.observeConsent().collectAsState(initial = false)

    NavHost(navController = navController, startDestination = "main") {
        // ── 首页 ──
        composable("main") {
            MainScreen(
                onNavigateToAIDiagnosis = { navController.navigate("ai_diagnosis") },
                onNavigateToDiet = { navController.navigate("diet") },
                onNavigateToMeridian = { navController.navigate("meridian") },
                onNavigateToHealthQuiz = { navController.navigate("health_quiz") },
                onNavigateToDisease = { navController.navigate("disease") },
                onNavigateToHerb = { navController.navigate("herb") },
                onNavigateToPrescription = { navController.navigate("prescription") },
                onNavigateToHealthTips = { navController.navigate("health_tips") },
                onNavigateToReport = { navController.navigate("report") },
                onNavigateToSettings = { navController.navigate("privacy_settings") }
            )
        }

        // ── AI诊断中心 ──
        composable("ai_diagnosis") {
            AIDiagnosisScreen(
                onBack = { navController.popBackStack() },
                onNavigateToFace = {
                    if (consentGranted) navController.navigate("face")
                    else navController.navigate("consent/face")
                },
                onNavigateToTongue = {
                    if (consentGranted) navController.navigate("tongue")
                    else navController.navigate("consent/tongue")
                },
                onNavigateToBP = { navController.navigate("bp") },
                onNavigateToPulse = { navController.navigate("pulse") }
            )
        }

        // ── 隐私同意页面 ──
        composable("consent/{target}") { backStackEntry ->
            val target = backStackEntry.arguments?.getString("target") ?: "face"
            PrivacyConsentScreen(
                onConsentGranted = {
                    navController.popBackStack("main", inclusive = false)
                    navController.navigate(target)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── 隐私设置 ──
        composable("privacy_settings") {
            PrivacySettingsScreen(onBack = { navController.popBackStack() })
        }

        // ── 诊断功能页面 ──
        composable("face") { FaceScanScreen(onBack = { navController.popBackStack() }) }
        composable("tongue") { TongueScanScreen(onBack = { navController.popBackStack() }) }
        composable("bp") { BloodPressureScreen(onBack = { navController.popBackStack() }) }
        composable("pulse") { PulseScanScreen(onBack = { navController.popBackStack() }) }
        composable("report") { HealthReportScreen(onBack = { navController.popBackStack() }) }

        // ── 辅助功能页面 ──
        composable("diet") { DietScreen(onBack = { navController.popBackStack() }) }
        composable("meridian") { MeridianScreen(onBack = { navController.popBackStack() }) }
        composable("health_quiz") { HealthQuizScreen(onBack = { navController.popBackStack() }) }
        composable("disease") { DiseaseScreen(onBack = { navController.popBackStack() }) }
        composable("herb") { HerbScreen(onBack = { navController.popBackStack() }) }
        composable("prescription") { PrescriptionScreen(onBack = { navController.popBackStack() }) }
        composable("health_tips") { HealthTipsScreen(onBack = { navController.popBackStack() }) }
    }
}