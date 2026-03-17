package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.shared.models.ApproverTaskItem
import com.kilu.pocketagent.shared.models.TaskLifecycleStep
import com.kilu.pocketagent.shared.models.ExecutionEvidenceUiModel
import com.kilu.pocketagent.core.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    var task by remember { mutableStateOf<ApproverTaskItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(taskId) {
        val cp = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
        val result = cp.getTask(taskId)
        if (result != null) {
            task = result
        } else {
            errorMsg = "Task not found."
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: "Task Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            val t = task!!
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState)
            ) {
                // Header Info
                Text("Task ID: ${t.task_id}", style = MaterialTheme.typography.labelSmall)
                Text(t.user_prompt ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                
                Spacer(Modifier.height(16.dp))

                // 1. Dual-Layer Timeline
                Text("Execution Timeline", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TimelineView(t)

                Spacer(Modifier.height(24.dp))

                // 2. Evidence Panel
                if (t.status == "DONE" || t.status == "FAILED") {
                    Text("Execution Evidence", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    EvidencePanel(t)
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("Execution results will appear here once the task reaches a terminal state.", 
                             modifier = Modifier.padding(16.dp),
                             style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineView(task: ApproverTaskItem) {
    val steps = deriveTimeline(task)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (step.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        if (index < steps.size - 1) {
                            Box(Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(step.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (step.isCurrent) FontWeight.Bold else FontWeight.Normal)
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(step.protocolState)
                        }
                        if (step.timestamp != null) {
                            Text(step.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (index < steps.size - 1) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun StatusBadge(state: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(state, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun EvidencePanel(task: ApproverTaskItem) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp)) {
            EvidenceRow("Outcome", if (task.status == "DONE") "SUCCESS" else "FAILURE")
            EvidenceRow("Artifact Hash (STDOUT)", "sha256:e3b0c442... (Placeholder)")
            EvidenceRow("Execution Log", "sha256:bc8912..." )
            
            task.final_report?.let { report ->
                Divider(Modifier.padding(vertical = 12.dp))
                Text("Normalized Summary", style = MaterialTheme.typography.labelLarge)
                Text(report, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun EvidenceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun deriveTimeline(task: ApproverTaskItem): List<TaskLifecycleStep> {
    val states = listOf("PLANNING", "NEEDS_PLAN_APPROVAL", "READY_FOR_EXECUTION", "EXECUTING", "DONE")
    val labels = mapOf(
        "PLANNING" to "Plan Draft Generation",
        "NEEDS_PLAN_APPROVAL" to "Pending Approver Authorization",
        "READY_FOR_EXECUTION" to "Authority Window Granted",
        "EXECUTING" to "Autonomous Hub Execution",
        "DONE" to "Execution Finalized",
        "FAILED" to "Terminal Failure",
        "CANCELLED" to "Revoked by Approver"
    )

    val currentIdx = states.indexOf(task.status)
    val result = mutableListOf<TaskLifecycleStep>()

    states.forEachIndexed { idx, s ->
        val label = labels[s] ?: s
        val isCompleted = if (currentIdx == -1) {
            // terminal states handle
            if (task.status == "FAILED" || task.status == "CANCELLED") idx < 4 else false
        } else idx <= currentIdx

        result.add(TaskLifecycleStep(
            label = label,
            protocolState = s,
            isCompleted = isCompleted,
            isCurrent = s == task.status
        ))
    }
    
    // Add terminal state if not DONE
    if (task.status == "FAILED" || task.status == "CANCELLED") {
        result.add(TaskLifecycleStep(
            label = labels[task.status] ?: task.status,
            protocolState = task.status,
            isCompleted = true,
            isCurrent = true,
            isError = task.status == "FAILED"
        ))
    }

    return result
}

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
