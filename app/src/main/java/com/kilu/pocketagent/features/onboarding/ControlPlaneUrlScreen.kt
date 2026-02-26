package com.kilu.pocketagent.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.BuildConfig
import com.kilu.pocketagent.core.storage.DeviceProfileStore

@Composable
fun ControlPlaneUrlScreen(store: DeviceProfileStore, onComplete: () -> Unit) {
    var url by remember { mutableStateOf(store.getControlPlaneUrl()) }

    val isValid = if (BuildConfig.ENFORCE_HTTPS) {
        url.startsWith("https://")
    } else {
        url.startsWith("https://") || url.startsWith("http://")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Connect to Control Plane", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
            isError = !isValid
        )
        if (!isValid) {
            val errMsg = if (BuildConfig.ENFORCE_HTTPS) "Production requires https://" else "Must be https:// or http://"
            Text(errMsg, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                store.setControlPlaneUrl(url.trimEnd('/'))
                onComplete()
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }
    }
}
