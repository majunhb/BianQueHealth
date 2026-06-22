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
        composable("main") {
            MainScreen(
                onNavigateToFace = {
                    if (consentGranted) {
                        navController.navigate("face")
                    } else {
                        navController.navigate("consent/face")
                    }
                },
                onNavigateToTongue = {
                    if (consentGranted) {
                        navController.navigate("tongue")
                    } else {
                        navController.navigate("consent/tongue")
                    }
                },
                onNavigateToBP = { navController.navigate("bp") },
                onNavigateToPulse = { navController.navigate("pulse") },
                onNavigateToReport = { navController.navigate("report") },
                onNavigateToSettings = { navController.navigate("privacy_settings") }
            )
        }

        // 隐私同意页面（带目标路由参数）
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

        // 隐私设置页面
        composable("privacy_settings") {
            PrivacySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("face") {
            FaceScanScreen(onBack = { navController.popBackStack() })
        }
        composable("tongue") {
            TongueScanScreen(onBack = { navController.popBackStack() })
        }
        composable("bp") {
            BloodPressureScreen(onBack = { navController.popBackStack() })
        }
        composable("pulse") {
            PulseScanScreen(onBack = { navController.popBackStack() })
        }
        composable("report") {
            HealthReportScreen(onBack = { navController.popBackStack() })
        }
    }
}