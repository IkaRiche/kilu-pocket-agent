package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.core.ui.components.StatusChip
import com.kilu.pocketagent.shared.models.TaskDetail
import com.kilu.pocketagent.shared.models.TaskLifecycleStep

// ─────────────────────────────────────────────────────────────────────────────
// TaskDetailScreen — R2 evidence view
// Shows 4 sections for a single task: State, Result, Execution, Evidence Preview.
// Rule: truthful — if data is absent, says so. No placeholders, no lorem, no N/A-spam.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    var task by remember { mutableStateOf<TaskDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(taskId) {
        val cp = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
        val result = cp.getTaskDetail(taskId)
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
                title = { Text(task?.title ?: "Task Details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val t = task!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── 1. Task State ─────────────────────────────────────────
                    SectionCard(title = "Task State") {
                        DetailRow("Status") {
                            StatusChip(t.status)
                        }
                        DetailRowText("Task ID", t.task_id)
                        t.target_runtime_id?.let { DetailRowText("Runtime", it) }
                        t.created_at?.let { DetailRowText("Created", formatTimestamp(it)) }
                        t.updated_at?.let { ts ->
                            if (isTerminal(t.status)) {
                                DetailRowText("Completed", formatTimestamp(ts))
                            }
                        }
                    }

                    // ── 2. Result ─────────────────────────────────────────────
                    SectionCard(title = "Result") {
                        val r = t.result?.result
                        if (r == null) {
                            AbsentNote(if (isTerminal(t.status)) "Result unavailable." else "Result pending execution.")
                        } else {
                            r.url?.let { DetailRowText("URL", it) }
                            r.final_url?.takeIf { it != r.url }?.let { DetailRowText("Final URL", it) }
                            r.summary?.let { DetailRowText("Summary", it) }
                            val headings = r.headings
                            if (!headings.isNullOrEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Headings", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                headings.take(8).forEachIndexed { i, h ->
                                    Row(Modifier.padding(vertical = 1.dp)) {
                                        Text("${i + 1}. ", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(h, style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (headings.size > 8) {
                                    Text("+ ${headings.size - 8} more", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                DetailRowText("Headings", "None extracted")
                            }
                        }
                    }

                    // ── 3. Execution ──────────────────────────────────────────
                    SectionCard(title = "Execution") {
                        val ev = t.result?.evidence
                        val r  = t.result?.result
                        if (ev == null) {
                            AbsentNote(if (isTerminal(t.status)) "No execution record stored." else "Execution not yet started.")
                        } else {
                            ev.adapter?.let  { DetailRowText("Adapter", it) }
                            ev.outcome?.let  { DetailRowText("Outcome", it.uppercase()) }
                            t.durationLabel()?.let { DetailRowText("Duration", it) }
                            r?.content_type?.let { DetailRowText("Content type", it) }
                            // char/heading count from summary if present
                            r?.summary?.let { summary ->
                                val charMatch = Regex("(\\d+) char").find(summary)
                                val headMatch = Regex("(\\d+) heading").find(summary)
                                if (charMatch != null || headMatch != null) {
                                    val facts = listOfNotNull(
                                        charMatch?.groupValues?.get(1)?.let { "$it chars" },
                                        headMatch?.groupValues?.get(1)?.let { "$it headings" }
                                    ).joinToString(", ")
                                    if (facts.isNotEmpty()) DetailRowText("Fetched", facts)
                                }
                            }
                            // Show failure if terminal-failure
                            t.failure?.let { f ->
                                Spacer(Modifier.height(4.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(4.dp))
                                f.code?.let    { DetailRowText("Failure code", it) }
                                f.message?.let { DetailRowText("Failure msg",  it) }
                            }
                        }
                    }

                    // ── 4. Evidence Preview ───────────────────────────────────
                    SectionCard(title = "Evidence Preview") {
                        val ev = t.result?.evidence
                        if (ev == null) {
                            if (t.failure != null) {
                                FactLine("Result stored", false)
                                FactLine("Evidence stored", false)
                                t.failure.code?.let { DetailRowText("Terminated with", it) }
                            } else {
                                AbsentNote("No evidence payload — task did not complete execution.")
                            }
                        } else {
                            FactLine("Result stored", ev.outcome == "success")
                            FactLine("Evidence stored", true)
                            FactLine("Executed on active runtime", t.target_runtime_id != null)
                            ev.stdout_hash?.let { hash ->
                                Spacer(Modifier.height(4.dp))
                                Text("Evidence hash", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    // Show first 20 chars of hash — enough to confirm it's real
                                    hash.take(32) + "…",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ── Timeline ──────────────────────────────────────────────
                    SectionCard(title = "Lifecycle") {
                        TaskTimelineView(t)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section card wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row variants
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailRowText(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun FactLine(label: String, present: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = if (present) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(
            if (present) "✓" else "✗",
            style = MaterialTheme.typography.bodySmall,
            color = if (present) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AbsentNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ─────────────────────────────────────────────────────────────────────────────
// Timeline
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TaskTimelineView(task: TaskDetail) {
    val steps = deriveDetailTimeline(task)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            step.isError    -> MaterialTheme.colorScheme.error
                            step.isCurrent  -> MaterialTheme.colorScheme.primary
                            step.isCompleted -> MaterialTheme.colorScheme.tertiary
                            else            -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(10.dp)
                    ) {}
                    if (index < steps.size - 1) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (step.isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                    if (step.timestamp != null) {
                        Text(step.timestamp, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun deriveDetailTimeline(task: TaskDetail): List<TaskLifecycleStep> {
    val orderedStates = listOf("PLANNING", "NEEDS_PLAN_APPROVAL", "READY_FOR_EXECUTION", "EXECUTING", "DONE")
    val labels = mapOf(
        "PLANNING"              to "Plan Generation",
        "NEEDS_PLAN_APPROVAL"   to "Awaiting Approval",
        "READY_FOR_EXECUTION"   to "Authority Granted",
        "EXECUTING"             to "Hub Executing",
        "DONE"                  to "Completed",
        "FAILED"                to "Failed",
        "CANCELLED"             to "Cancelled"
    )
    val currentIdx = orderedStates.indexOf(task.status)
    val result = mutableListOf<TaskLifecycleStep>()

    orderedStates.forEachIndexed { idx, state ->
        val isCompleted = if (currentIdx == -1) idx < orderedStates.size - 1 else idx <= currentIdx
        result.add(TaskLifecycleStep(
            label        = labels[state] ?: state,
            protocolState = state,
            isCompleted  = isCompleted,
            isCurrent    = state == task.status,
            timestamp    = null
        ))
    }

    if (task.status == "FAILED" || task.status == "CANCELLED") {
        result.add(TaskLifecycleStep(
            label        = labels[task.status] ?: task.status,
            protocolState = task.status,
            isCompleted  = true,
            isCurrent    = true,
            isError      = task.status == "FAILED"
        ))
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun isTerminal(status: String) =
    status in setOf("DONE", "FAILED", "CANCELLED")

/** Trim ISO timestamp to readable local format (no TZ math, just strip the T/Z) */
private fun formatTimestamp(ts: String): String =
    ts.replace("T", " ").take(19)
