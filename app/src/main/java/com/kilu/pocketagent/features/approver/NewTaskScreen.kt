package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.shared.models.CreateTaskReq
import com.kilu.pocketagent.shared.models.HubDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(apiClient: ApiClient, onCreated: (String) -> Unit, onCancel: () -> Unit) {
    var titleInput by remember { mutableStateOf("") }
    var promptInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // ── 10E: Hub selection state ────────────────────────────────────────────
    var availableHubs by remember { mutableStateOf<List<HubDevice>>(emptyList()) }
    var selectedHub by remember { mutableStateOf<HubDevice?>(null) }
    var hubDropdownExpanded by remember { mutableStateOf(false) }
    var hubsLoaded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Load Hub devices on composition
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val controlPlane = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
            val devices = controlPlane.getDevices()
            val hubs = devices
                ?.filter { it.device_type == "HUB" && it.runtime_id != null }
                ?: emptyList()
            withContext(Dispatchers.Main) {
                availableHubs = hubs
                // Auto-select if exactly 1 hub (mirrors server-side auto-bind)
                if (hubs.size == 1) selectedHub = hubs[0]
                hubsLoaded = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("New Task") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding().imePadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (errorMsg != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                "Error: $errorMsg",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (titleInput.isBlank()) { errorMsg = "Title is required."; return@Button }
                            if (promptInput.isBlank()) { errorMsg = "Prompt is required."; return@Button }
                            isSubmitting = true
                            errorMsg = null
                            scope.launch {
                                try {
                                    val inputs = if (urlInput.isNotBlank())
                                        mapOf("base_url" to urlInput.trim()) else null
                                    val reqPayload = CreateTaskReq(
                                        title = titleInput.trim(),
                                        user_prompt = promptInput.trim(),
                                        executor_preference = "HUB_PREFERRED",
                                        target_runtime_id = selectedHub?.runtime_id,  // 10E: explicit or null
                                        inputs = inputs
                                    )
                                    val controlPlane = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
                                    val data = controlPlane.createTask(reqPayload)
                                    if (data != null) {
                                        onCreated(data.task_id)
                                    } else {
                                        errorMsg = "Failed to create task."
                                    }
                                } catch (e: Exception) {
                                    errorMsg = "Network Error: ${e.message}"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSubmitting && titleInput.isNotBlank() && promptInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSubmitting) "Creating…" else "Create Task")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // Title
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Task title") },
                placeholder = { Text("e.g. Collect pricing from competitor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Prompt
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text("What should the agent do?") },
                placeholder = { Text("e.g. Go to the pricing page and extract all plan names and prices") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(12.dp))

            // URL (optional)
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Target URL (optional)") },
                placeholder = { Text("https://example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 10E: Hub selector ───────────────────────────────────────────
            // Show hub binding info or selector depending on how many hubs are available.
            if (hubsLoaded) {
                when {
                    availableHubs.isEmpty() -> {
                        // No hubs — task submitted without explicit binding
                        Text(
                            "No Hub currently available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    availableHubs.size == 1 -> {
                        // Single hub — auto-bind, show as static label (mirrors server behaviour)
                        Text(
                            "Hub: ${availableHubs[0].display_name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        availableHubs[0].runtime_id?.let { rtId ->
                            Text(
                                "Runtime: $rtId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    else -> {
                        // Multiple hubs — show selector (explicit selection required for HUB_ONLY tasks)
                        Text(
                            "Select execution hub",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = hubDropdownExpanded,
                            onExpandedChange = { hubDropdownExpanded = !hubDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedHub?.let {
                                    "${it.display_name}  ·  ${it.runtime_id}"
                                } ?: "Auto-select (most recent eligible hub)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Hub") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hubDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = hubDropdownExpanded,
                                onDismissRequest = { hubDropdownExpanded = false }
                            ) {
                                // "Auto" option — let server pick
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Auto-select", style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Server picks most recently active hub",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedHub = null
                                        hubDropdownExpanded = false
                                    }
                                )
                                HorizontalDivider()
                                availableHubs.forEach { hub ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(hub.display_name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "${hub.runtime_id}  ·  ${hub.runtime_status ?: "unknown"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedHub = hub
                                            hubDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        // Show auto-selected info if nothing explicitly chosen
                        if (selectedHub == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "For HUB_ONLY tasks, you must select a hub explicitly.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick templates
            Text("Quick templates:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            val templates = listOf(
                Triple("Collect & Report", "Navigate to the URL and extract the main content. Summarize key information.", "https://en.wikipedia.org/wiki/Headless_browser"),
                Triple("Check Pricing", "Find the pricing page and extract all plan names, prices, and features.", "https://example.com"),
                Triple("Test Error Handling", "Attempt to visit the URL. Report what happened.", "https://httpstat.us/403")
            )
            templates.forEach { (title, prompt, url) ->
                TextButton(
                    onClick = {
                        titleInput = title
                        promptInput = prompt
                        urlInput = url
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
