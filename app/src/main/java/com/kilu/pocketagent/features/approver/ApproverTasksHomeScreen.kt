package com.kilu.pocketagent.features.approver

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.core.ui.components.EmptyState
import com.kilu.pocketagent.core.ui.components.KiluSectionHeader
import com.kilu.pocketagent.core.ui.components.KiluStatBadge
import com.kilu.pocketagent.core.ui.components.TaskCard
import com.kilu.pocketagent.core.ui.theme.StatusApproved
import com.kilu.pocketagent.core.ui.theme.StatusPending
import com.kilu.pocketagent.shared.models.ApproverTaskItem
import com.kilu.pocketagent.shared.models.QuotasResp
import com.kilu.pocketagent.shared.models.WorkflowGrantListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproverTasksHomeScreen(
    apiClient: ApiClient,
    onSessionInvalid: () -> Unit,
    onNewTaskClick: () -> Unit,
    onTaskClick: (String, String) -> Unit,
    onInboxClick: () -> Unit,
    onPairHub: () -> Unit,
    onWorkflowGrantClick: (String) -> Unit = {},
) {
    var tasks by remember { mutableStateOf<List<ApproverTaskItem>>(emptyList()) }
    var pendingGrants by remember { mutableStateOf<List<WorkflowGrantListItem>>(emptyList()) }
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
                // Surface PLANNING tasks that have workflow_grant_id (Phase B) as pending-approval items.
                // NOTE: workflow_grant_id (wfg_ prefix) is the Phase B field.
                //       active_grant_id (grt_ prefix) is the legacy per-step field — do NOT use for workflow grants.
                val planningWithGrant = result.filter {
                    it.status == "PLANNING" && it.workflow_grant_id != null
                }
                pendingGrants = if (planningWithGrant.isNotEmpty()) {
                    planningWithGrant
                        .mapNotNull { t ->
                            val gid = t.workflow_grant_id ?: return@mapNotNull null
                            WorkflowGrantListItem(
                                grant_id = gid,
                                status = "active",
                                total_steps = planningWithGrant.count { it.workflow_grant_id == gid },
                                target_runtime_id = "",
                                expires_at = "",
                                created_at = t.created_at,
                            )
                        }
                        .distinctBy { it.grant_id }
                } else {
                    emptyList()
                }
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

    LaunchedEffect(Unit) {
        loadTasks()
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            loadTasks()
        }
    }

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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KiluStatBadge("Active", "$activeCount", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    KiluStatBadge("Pending", "$pendingCount", StatusPending, Modifier.weight(1f))
                    KiluStatBadge("Done", "$doneCount", StatusApproved, Modifier.weight(1f))
                    KiluStatBadge("Credits", "${q.planner_credits}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
            }

            // ── Pair Hub quick action ──
            OutlinedButton(
                onClick = onPairHub,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("+ Pair Hub", style = MaterialTheme.typography.labelLarge)
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

            // ── Pending Workflow Grants (E3.2 Phase B) ──
            // Shows when tasks have workflow_grant_id (wfg_ prefix), meaning they're awaiting
            // one-tap APPROVE ALL before the bridge can execute them atomically.
            if (pendingGrants.isNotEmpty()) {
                KiluSectionHeader(label = "Workflow Grants (${pendingGrants.size} pending approval)")
                pendingGrants.forEach { grant ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        onClick = { onWorkflowGrantClick(grant.grant_id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "WORKFLOW GRANT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    grant.grant_id.take(18) + "\u2026",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "${grant.total_steps} step${if (grant.total_steps != 1) "s" else ""} pending approval",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Button(
                                onClick = { onWorkflowGrantClick(grant.grant_id) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Review", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // ── Task List ──
            KiluSectionHeader(label = "Tasks")
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
                                icon = "\uD83D\uDCCB",
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
                                    false
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
                                // PLANNING tasks with workflow_grant_id -> route to WorkflowGrantDetailScreen
                                // All other tasks -> route to PlanPreviewScreen or TaskDetailScreen
                                val grantId = task.workflow_grant_id
                                TaskCard(
                                    task = task,
                                    onClick = {
                                        if (grantId != null && task.status == "PLANNING") {
                                            onWorkflowGrantClick(grantId)
                                        } else {
                                            onTaskClick(task.task_id, task.status)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
