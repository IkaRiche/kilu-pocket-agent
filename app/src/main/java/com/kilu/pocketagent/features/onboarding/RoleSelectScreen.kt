package com.kilu.pocketagent.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role

@Composable
fun RoleSelectScreen(store: DeviceProfileStore, onRoleSelected: () -> Unit) {
    var selectedRole by remember { mutableStateOf(store.getRole()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Your Role", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row {
            RadioButton(selected = selectedRole == Role.APPROVER, onClick = { selectedRole = Role.APPROVER })
            Text("Approver (Master Device)", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        }
        Row {
            RadioButton(selected = selectedRole == Role.HUB, onClick = { selectedRole = Role.HUB })
            Text("Hub (Execution Worker)", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                selectedRole?.let {
                    store.setRole(it)
                    onRoleSelected()
                }
            },
            enabled = selectedRole != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}
