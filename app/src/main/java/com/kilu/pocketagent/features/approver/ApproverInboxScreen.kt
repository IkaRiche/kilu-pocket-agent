package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.InboxEpisode
import com.kilu.pocketagent.shared.models.ResultPayloadView
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun ApproverInboxScreen(apiClient: ApiClient, onBack: () -> Unit) {
    var episodes by remember { mutableStateOf<List<InboxEpisode>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    fun loadInbox() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val req = Request.Builder()
                    .url("\${apiClient.getBaseUrl()}/v1/inbox?max=50")
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "[]"
                    episodes = jsonParser.decodeFromString<List<InboxEpisode>>(bodyStr)
                } else {
                    errorMsg = ErrorHandler.parseError(resp)
                }
            } catch(e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadInbox() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Approver Inbox", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { loadInbox() }) { Text("\uD83D\uDD04") }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (errorMsg != null) {
            Text("Error: \$errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(episodes) { ep ->
                    InboxCard(ep, apiClient, jsonParser) { loadInbox() }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (episodes.isEmpty()) {
                    item { Text("No unread items in Inbox.") }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun InboxCard(ep: InboxEpisode, apiClient: ApiClient, jsonParser: Json, onAcked: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ep.event_type, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (ep.requires_ack) Text("NEW", color = MaterialTheme.colorScheme.error)
            }
            Text("Task: \${ep.task_id.take(8)}...", style = MaterialTheme.typography.bodySmall)
            Text(ep.created_at, style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isExpanded && ep.payload != null) {
                if (ep.event_type == "RESULT_READY") {
                    try {
                        val resultView = jsonParser.decodeFromJsonElement<ResultPayloadView>(ep.payload.getValue("result"))
                        Text("Source: \${resultView.url}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Summary: \${resultView.summary}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(resultView.extracted_text, style = MaterialTheme.typography.bodySmall, maxLines = 10)
                    } catch (e: Exception) {
                        Text("Failed to parse result details.", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(ep.payload.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
            
            if (ep.requires_ack) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        try {
                            val req = Request.Builder()
                                .url("\${apiClient.getBaseUrl()}/v1/inbox/ack")
                                .post("{\"episode_id\":\"\${ep.episode_id}\"}".toRequestBody("application/json".toMediaType()))
                                .build()
                            val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                            if (resp.isSuccessful) {
                                isExpanded = true
                                onAcked()
                            }
                        } catch (e: Exception) { }
                    }
                }) {
                    Text("Read & Acknowledge")
                }
            } else {
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Hide Details" else "View Details")
                }
            }
        }
    }
}
