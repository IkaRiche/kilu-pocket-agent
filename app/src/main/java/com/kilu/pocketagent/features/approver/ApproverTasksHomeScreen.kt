package com.kilu.pocketagent.features.approver

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.ui.components.EmptyState
import com.kilu.pocketagent.core.ui.components.StatusChip
import com.kilu.pocketagent.core.ui.components.TaskCard
import com.kilu.pocketagent.shared.models.ApproverTaskItem
import com.kilu.pocketagent.shared.models.QuotasResp
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproverTasksHomeScreen(
    apiClient: ApiClient,
    onSessionInvalid: () -> Unit,
    onNewTaskClick: () -> Unit,
    onTaskClick: (String, String) -> Unit,
    onInboxClick: () -> Unit,
    onPairHub: () -> Unit
) {
    var tasks by remember { mutableStateOf<List<ApproverTaskItem>>(emptyList()) }
    var quotas by remember { mutableStateOf<QuotasResp?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    val loadQuotas = {
        scope.launch {
            try {
                val req = Request.Builder().url(apiClient.apiUrl("quotas")).get().build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                if (resp.isSuccessful) {
                    quotas = jsonParser.decodeFromString<QuotasResp>(resp.body?.string() ?: "")
                }
            } catch (_: Exception) {}
        }
    }

    val loadTasks = {
        scope.launch {
            isLoading = true
            errorMsg = null
            loadQuotas()
            try {
                val req = Request.Builder()
                    .url(apiClient.apiUrl("tasks?limit=10"))
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "[]"
                    tasks = jsonParser.decodeFromString<List<ApproverTaskItem>>(body)
                } else if (resp.code == 401 || resp.code == 403) {
                    onSessionInvalid()
                } else {
                    errorMsg = ErrorHandler.parseError(resp)
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadTasks() }

    // Count stats
    val activeCount = tasks.count { it.status in listOf("READY_FOR_EXECUTION", "EXECUTING") }
    val pendingCount = tasks.count { it.status in listOf("NEEDS_PLAN_APPROVAL", "PLANNING") }
    val doneCount = tasks.count { it.status == "DONE" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KiLu Agent", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    IconButton(onClick = onInboxClick) {
                        Icon(Icons.Filled.Email, contentDescription = "Inbox")
                    }
                    IconButton(onClick = { loadTasks() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTaskClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Task")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── Stats Row ──
            quotas?.let { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Active",
                        value = "$activeCount",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Pending",
                        value = "$pendingCount",
                        color = com.kilu.pocketagent.core.ui.theme.StatusPending,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Done",
                        value = "$doneCount",
                        color = com.kilu.pocketagent.core.ui.theme.StatusApproved,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Credits",
                        value = "${q.planner_credits}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Quick Actions ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPairHub,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Pair Hub")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Error ──
            if (errorMsg != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMsg ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Task List ──
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onTaskClick(task.task_id, task.status) }
                        )
                    }

                    if (tasks.isEmpty()) {
                        item {
                            EmptyState(
                                icon = "📋",
                                title = "No tasks yet",
                                subtitle = "Tap + to create your first task"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
