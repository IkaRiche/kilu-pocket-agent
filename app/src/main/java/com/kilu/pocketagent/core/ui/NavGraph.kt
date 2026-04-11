package com.kilu.pocketagent.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.kilu.pocketagent.features.pairing.HubPairInitScreen
import com.kilu.pocketagent.features.pairing.DiagnosticsScreen
import com.kilu.pocketagent.features.pairing.HubScanScreen
import com.kilu.pocketagent.features.pairing.HubOfferDetailsScreen
import com.kilu.pocketagent.shared.models.QRPayload
import com.kilu.pocketagent.features.approver.WorkflowGrantDetailScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val store = remember { DeviceProfileStore(context) }
    val apiClient = remember { ApiClient(store) }
    var scannedPayload by remember { mutableStateOf<QRPayload?>(null) }

    val startDestination = when {
        store.getRole() == null -> "welcome"
        store.getRole() == Role.APPROVER && store.getSessionToken() != null -> "approver_home"
        store.getRole() == Role.HUB && store.getSessionToken() != null -> "hub_home"
        store.getRole() == Role.APPROVER -> "approver_register"
        store.getRole() == Role.HUB -> "hub_unpaired"
        else -> "welcome"
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("welcome") { WelcomeScreen(onContinue = { navController.navigate("role_select") }) }
        composable("role_select") {
            RoleSelectScreen(store = store, onRoleSelected = { navController.navigate("url_config") })
        }
        composable("url_config") {
            ControlPlaneUrlScreen(
                store = store,
                onComplete = {
                    val dest = if (store.getRole() == Role.APPROVER) "approver_register" else "hub_unpaired"
                    navController.navigate(dest) { popUpTo("welcome") { inclusive = true } }
                }
            )
        }

        composable("approver_register") {
            ApproverPairingInitScreen(
                apiClient = apiClient, store = store,
                onPaired = {
                    navController.navigate("approver_home") { popUpTo("approver_register") { inclusive = true } }
                }
            )
        }

        composable("approver_home") {
            ApproverScaffold(
                apiClient = apiClient, store = store,
                onSessionInvalid = {
                    store.clearPairing()
                    navController.navigate("approver_register") { popUpTo("approver_home") { inclusive = true } }
                },
                onNewTaskClick = { navController.navigate("approver_new_task") },
                onTaskClick = { taskId, status ->
                    if (status == "NEEDS_PLAN_APPROVAL" || status == "PLANNING") navController.navigate("approver_plan/$taskId")
                    else navController.navigate("approver_task_detail/$taskId")
                },
                onWorkflowGrantClick = { grantId -> navController.navigate("approver_workflow_grant/$grantId") },
                onInboxClick = { navController.navigate("approver_inbox") },
                onPairHub = { navController.navigate("hub_pair_init") },
                onDiagnostics = { navController.navigate("diagnostics") },
                onResetPairing = {
                    store.clearPairing()
                    navController.navigate("welcome") { popUpTo("approver_home") { inclusive = true } }
                },
                onSwitchRole = {
                    store.clearPairing()
                    navController.navigate("role_select") { popUpTo("approver_home") { inclusive = true } }
                }
            )
        }

        composable("hub_pair_init") {
            HubPairInitScreen(apiClient = apiClient, store = store, onBack = { navController.navigateUp() })
        }

        composable("approver_new_task") {
            com.kilu.pocketagent.features.approver.NewTaskScreen(
                apiClient = apiClient,
                onCreated = { taskId -> navController.navigate("approver_plan/$taskId") { popUpTo("approver_home") } },
                onCancel = { navController.navigateUp() }
            )
        }
        composable("approver_plan/{taskId}") { backStackEntry ->
            val tId = backStackEntry.arguments?.getString("taskId") ?: ""
            com.kilu.pocketagent.features.approver.PlanPreviewScreen(
                taskId = tId, apiClient = apiClient,
                onApproved = { navController.navigate("approver_home") { popUpTo("approver_home") { inclusive = true } } },
                onBack = { navController.navigateUp() }
            )
        }
        composable("approver_task_detail/{taskId}") { backStackEntry ->
            val tId = backStackEntry.arguments?.getString("taskId") ?: ""
            com.kilu.pocketagent.features.approver.TaskDetailScreen(
                taskId = tId, apiClient = apiClient, onBack = { navController.navigateUp() }
            )
        }
        composable("approver_inbox") {
            com.kilu.pocketagent.features.approver.ApproverInboxScreen(
                apiClient = apiClient,
                onResolveRequested = { taskId -> navController.navigate("approver_resolve/$taskId") },
                onBack = { navController.navigateUp() }
            )
        }
        composable("approver_resolve/{taskId}") { backStackEntry ->
            val tId = backStackEntry.arguments?.getString("taskId") ?: ""
            com.kilu.pocketagent.features.approver.AssumptionResolutionScreen(
                taskId = tId, apiClient = apiClient,
                onResolved = { navController.navigateUp() }, onBack = { navController.navigateUp() }
            )
        }

        // E3.2 Phase B — B7: Workflow Grant APPROVE ALL
        composable("approver_workflow_grant/{grantId}") { backStackEntry ->
            val gId = backStackEntry.arguments?.getString("grantId") ?: ""
            WorkflowGrantDetailScreen(
                grantId = gId, apiClient = apiClient, store = store,
                onApproved = { navController.navigate("approver_home") { popUpTo("approver_home") { inclusive = true } } },
                onDenied = { navController.navigateUp() },
                onBack = { navController.navigateUp() }
            )
        }

        composable("hub_unpaired") {
            PairingHomeScreen(
                store = store, apiClient = apiClient,
                onChangeRole = { navController.navigate("role_select") { popUpTo("hub_unpaired") { inclusive = true } } },
                onPairInit = { navController.navigate("hub_scan") },
                onDiagnostics = { navController.navigate("diagnostics") }
            )
        }

        composable("hub_home") {
            com.kilu.pocketagent.features.hub.HubDashboardScreen(
                apiClient = apiClient,
                onSessionInvalid = { store.clearPairing(); navController.navigate("hub_unpaired") { popUpTo("hub_home") { inclusive = true } } },
                onResetPairing = { store.clearPairing(); navController.navigate("hub_unpaired") { popUpTo("hub_home") { inclusive = true } } },
                onSwitchRole = { store.clearPairing(); navController.navigate("role_select") { popUpTo("hub_home") { inclusive = true } } },
                onDiagnostics = { navController.navigate("diagnostics") }
            )
        }

        composable("hub_scan") {
            HubScanScreen(onScanSuccess = { payload -> scannedPayload = payload; navController.navigate("hub_offer_details") })
        }
        composable("hub_offer_details") {
            val payload = scannedPayload
            if (payload != null) {
                HubOfferDetailsScreen(
                    payload = payload, apiClient = apiClient, store = store,
                    onPaired = { navController.navigate("hub_home") { popUpTo("hub_unpaired") { inclusive = true } } }
                )
            } else { Text("Error: No Payload") }
        }

        composable("diagnostics") {
            DiagnosticsScreen(apiClient = apiClient, store = store, onBack = { navController.navigateUp() })
        }
    }
}
