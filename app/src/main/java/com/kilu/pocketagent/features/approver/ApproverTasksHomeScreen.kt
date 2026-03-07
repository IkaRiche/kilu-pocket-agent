package com.kilu.pocketagent.features.approver

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.kilu.pocketagent.core.network.ControlPlaneApi

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
    var taskToDelete by remember { mutableStateOf<ApproverTaskItem?>(null) }
    
    val scope = rememberCoroutineScope()
    val controlPlane = remember {
        ControlPlaneApi(apiClient.client, apiClient.apiUrl("")) {
            onSessionInvalid()
        }
    }

    val loadQuotas = {
        scope.launch {
            val q = controlPlane.getQuotas()
            if (q != null) quotas = q
        }
    }

    val loadTasks: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMsg = null
            loadQuotas()
            val result = controlPlane.getTasks(20)
            if (result != null) {
                tasks = result
            } else {
                errorMsg = "Failed to load tasks."
            }
            isLoading = false
        }
    }

    val cancelTask: (String) -> Unit = { taskId ->
        scope.launch {
            val success = controlPlane.cancelTask(taskId)
            if (!success) errorMsg = "Cancel failed."
            loadTasks()
        }
    }

    LaunchedEffect(Unit) { loadTasks() }

    // Delete confirmation dialog
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Cancel Task?") },
            text = { Text("Cancel task \"${task.title ?: task.task_id.take(8)}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelTask(task.task_id)
                    taskToDelete = null
                }) { Text("Cancel Task", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Keep") }
            }
        )
    }

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
            FloatingActionButton(onClick = onNewTaskClick) {
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
                    StatCard("Active", "$activeCount", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    StatCard("Pending", "$pendingCount", com.kilu.pocketagent.core.ui.theme.StatusPending, Modifier.weight(1f))
                    StatCard("Done", "$doneCount", com.kilu.pocketagent.core.ui.theme.StatusApproved, Modifier.weight(1f))
                    StatCard("Credits", "${q.planner_credits}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
            }

            // ── Quick Actions ──
            OutlinedButton(
                onClick = onPairHub,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Pair Hub")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Error ──
            if (errorMsg != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = errorMsg ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Task List with swipe-to-delete ──
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tasks.isEmpty()) {
                        item {
                            EmptyState(
                                icon = "📋",
                                title = "No tasks yet",
                                subtitle = "Tap + to create your first task"
                            )
                        }
                    } else {
                        items(tasks, key = { it.task_id }) { task ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        taskToDelete = task
                                    }
                                    false // Don't auto-dismiss; show dialog first
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                shape = MaterialTheme.shapes.medium
                                            )
                                            .padding(end = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Cancel",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            ) {
                                TaskCard(
                                    task = task,
                                    onClick = { onTaskClick(task.task_id, task.status) }
                                )
                            }
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
