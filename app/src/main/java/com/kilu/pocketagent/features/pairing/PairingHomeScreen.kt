package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.storage.DeviceProfileStore

@Composable
fun PairingHomeScreen(store: DeviceProfileStore, onChangeRole: () -> Unit) {
    val role = store.getRole()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pairing Dashboard (${role?.name})", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onChangeRole, modifier = Modifier.fillMaxWidth()) {
            Text("Switch Role")
        }
    }
}
