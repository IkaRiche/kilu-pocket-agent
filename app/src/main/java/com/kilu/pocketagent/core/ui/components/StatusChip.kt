package com.kilu.pocketagent.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.ui.theme.*

@Composable
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status.uppercase()) {
        "APPROVED", "DONE" -> Triple(
            StatusApproved.copy(alpha = 0.15f),
            StatusApproved,
            if (status.uppercase() == "DONE") "Done" else "Approved"
        )
        "NEEDS_PLAN_APPROVAL", "PENDING", "AWAITING_APPROVAL" -> Triple(
            StatusPending.copy(alpha = 0.15f),
            StatusPending,
            "Awaiting Approval"
        )
        "READY_FOR_EXECUTION", "EXECUTING" -> Triple(
            StatusExecuting.copy(alpha = 0.15f),
            StatusExecuting,
            if (status.uppercase() == "EXECUTING") "Executing" else "Ready"
        )
        "PLANNING" -> Triple(
            Tertiary.copy(alpha = 0.15f),
            Tertiary,
            "Planning"
        )
        "FAILED", "ERROR" -> Triple(
            StatusFailed.copy(alpha = 0.15f),
            StatusFailed,
            "Failed"
        )
        "SUMMARIZING" -> Triple(
            Tertiary.copy(alpha = 0.15f),
            Tertiary,
            "Summarizing"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            status.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(textColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
