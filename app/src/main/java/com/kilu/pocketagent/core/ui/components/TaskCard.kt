package com.kilu.pocketagent.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.ui.theme.*
import com.kilu.pocketagent.shared.models.ApproverTaskItem

@Composable
fun TaskCard(
    task: ApproverTaskItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Map status to accent color for the card border
    val accentColor = when (task.status.uppercase()) {
        "DONE", "APPROVED" -> StatusApproved
        "NEEDS_PLAN_APPROVAL", "PLANNING" -> StatusPending
        "READY_FOR_EXECUTION", "EXECUTING" -> StatusExecuting
        "FAILED", "ERROR" -> StatusFailed
        else -> null
    }

    KiluCard(
        modifier = modifier.clickable(onClick = onClick),
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title ?: task.user_prompt ?: "Untitled Task",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "#${task.task_id.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status = task.status)
        }

        if (task.status == "DONE" && task.final_report != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = task.final_report.take(100) + if (task.final_report.length > 100) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
