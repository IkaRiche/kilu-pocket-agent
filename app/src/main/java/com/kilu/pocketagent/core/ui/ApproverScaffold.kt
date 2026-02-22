package com.kilu.pocketagent.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.features.approver.ApproverTasksHomeScreen
import com.kilu.pocketagent.features.approver.DevicesScreen
import com.kilu.pocketagent.features.approver.SettingsScreen

enum class ApproverTab(val label: String, val icon: ImageVector) {
    TASKS("Tasks", Icons.Filled.Shield),
    DEVICES("Devices", Icons.Outlined.Devices),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

@Composable
fun ApproverScaffold(
    apiClient: ApiClient,
    store: DeviceProfileStore,
    onSessionInvalid: () -> Unit,
    onNewTaskClick: () -> Unit,
    onTaskClick: (String, String) -> Unit,
    onInboxClick: () -> Unit,
    onPairHub: () -> Unit,
    onDiagnostics: () -> Unit,
    onResetPairing: () -> Unit,
    onSwitchRole: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ApproverTab.TASKS) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                ApproverTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            ApproverTab.TASKS -> ApproverTasksHomeScreen(
                apiClient = apiClient,
                onSessionInvalid = onSessionInvalid,
                onNewTaskClick = onNewTaskClick,
                onTaskClick = onTaskClick,
                onInboxClick = onInboxClick,
                onPairHub = { selectedTab = ApproverTab.DEVICES }
            )
            ApproverTab.DEVICES -> DevicesScreen(
                store = store,
                onPairHub = onPairHub,
                onDiagnostics = onDiagnostics
            )
            ApproverTab.SETTINGS -> SettingsScreen(
                store = store,
                onResetPairing = onResetPairing,
                onSwitchRole = onSwitchRole
            )
        }
    }
}
