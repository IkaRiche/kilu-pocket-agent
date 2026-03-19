package com.kilu.pocketagent.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.ui.theme.*

// ─────────────────────────────────────────────────────────────
// KiluCard — main surface container for authority blocks,
// device cards, forms. Subtle border + tonal elevation.
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = accentColor?.copy(alpha = 0.35f)
        ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// ─────────────────────────────────────────────────────────────
// KiluPrimaryButton — the main action CTA.
// Gradient fill, clean disabled state, proper height.
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = "Loading…"
) {
    val gradient = Brush.horizontalGradient(
        colors = if (enabled) listOf(Primary, Color(0xFF9873FF))
        else listOf(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .then(
                if (enabled) Modifier else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(loadingText, fontWeight = FontWeight.SemiBold)
            } else {
                Text(text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// KiluTopBar — screen header with accent underline.
// Used for section titles at the top of content areas.
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluTopBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        // Accent underline — the primary brand line
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(listOf(Primary, Secondary))
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────
// KiluSectionHeader — in-page section separators.
// Compact label + subtle accent line on the left.
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val color = accentColor ?: Primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(
                1.2f, androidx.compose.ui.unit.TextUnitType.Sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────
// KiluStatBadge — stat card with gradient background tint.
// Used in dashboard stat row (Active / Pending / Done / Credits).
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluStatBadge(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().let {
            androidx.compose.foundation.BorderStroke(
                1.dp, accentColor.copy(alpha = 0.3f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// KiluDivider — subtle horizontal line with spacing
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        thickness = 0.5.dp
    )
}

// ─────────────────────────────────────────────────────────────
// KiluBindingRow — key/value row for authority binding section
// ─────────────────────────────────────────────────────────────
@Composable
fun KiluBindingRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
