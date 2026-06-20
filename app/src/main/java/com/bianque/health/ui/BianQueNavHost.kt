package com.bianque.health.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bianque.health.ui.screens.BloodPressureScreen
import com.bianque.health.ui.screens.FaceScanScreen
import com.bianque.health.ui.screens.HealthReportScreen
import com.bianque.health.ui.screens.PulseDiagnosisScreen
import com.bianque.health.ui.screens.TongueScanScreen

@Composable
fun BianQueNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToFace = { navController.navigate("face") },
                onNavigateToTongue = { navController.navigate("tongue") },
                onNavigateToBP = { navController.navigate("bp") },
                onNavigateToPulse = { navController.navigate("pulse") },
                onNavigateToReport = { navController.navigate("report") }
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
            PulseDiagnosisScreen(onBack = { navController.popBackStack() })
        }
        composable("report") {
            HealthReportScreen(onBack = { navController.popBackStack() })
        }
    }
}