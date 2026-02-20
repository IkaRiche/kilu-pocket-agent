package com.kilu.pocketagent.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import com.kilu.pocketagent.features.onboarding.WelcomeScreen
import com.kilu.pocketagent.features.onboarding.RoleSelectScreen
import com.kilu.pocketagent.features.onboarding.ControlPlaneUrlScreen
import com.kilu.pocketagent.features.pairing.PairingHomeScreen
import com.kilu.pocketagent.features.pairing.ApproverPairingInitScreen
import com.kilu.pocketagent.features.pairing.DiagnosticsScreen
import com.kilu.pocketagent.features.pairing.HubScanScreen
import com.kilu.pocketagent.features.pairing.HubOfferDetailsScreen
import com.kilu.pocketagent.shared.models.QRPayload

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val store = remember { DeviceProfileStore(context) }
    val apiClient = remember { ApiClient(store) }
    var scannedPayload: QRPayload? = null
    
    val startDestination = if (store.getRole() == null) "welcome" else "pairing_home"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") {
            WelcomeScreen(onContinue = { navController.navigate("role_select") })
        }
        composable("role_select") {
            RoleSelectScreen(
                store = store,
                onRoleSelected = { navController.navigate("url_config") }
            )
        }
        composable("url_config") {
            ControlPlaneUrlScreen(
                store = store,
                onComplete = {
                    navController.navigate("pairing_home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }
        composable("pairing_home") {
            PairingHomeScreen(
                store = store,
                apiClient = apiClient,
                onChangeRole = {
                    navController.navigate("role_select") {
                        popUpTo("pairing_home") { inclusive = true }
                    }
                },
                onPairInit = {
                    if (store.getRole() == Role.APPROVER) navController.navigate("approver_init")
                    else navController.navigate("hub_scan")
                },
                onDiagnostics = { navController.navigate("diagnostics") }
            )
        }
        composable("approver_init") {
            ApproverPairingInitScreen(
                apiClient = apiClient,
                store = store,
                onPaired = {
                    navController.navigate("pairing_home") {
                        popUpTo("pairing_home") { inclusive = true }
                    }
                }
            )
        }
        composable("hub_scan") {
            HubScanScreen(
                onScanSuccess = { payload ->
                    scannedPayload = payload
                    navController.navigate("hub_offer_details")
                }
            )
        }
        composable("hub_offer_details") {
            scannedPayload?.let { payload ->
                HubOfferDetailsScreen(
                    payload = payload,
                    apiClient = apiClient,
                    store = store,
                    onPaired = {
                        navController.navigate("pairing_home") {
                            popUpTo("pairing_home") { inclusive = true }
                        }
                    }
                )
            } ?: Text("Error: No Payload")
        }
        composable("diagnostics") {
            DiagnosticsScreen(
                store = store,
                onBack = { navController.navigateUp() }
            )
        }
    }
}
