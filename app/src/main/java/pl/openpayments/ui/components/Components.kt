package pl.openpayments.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.openpayments.data.Category
import pl.openpayments.data.Transaction
import pl.openpayments.data.TxSource
import pl.openpayments.domain.formatMoney
import pl.openpayments.ui.theme.Danger
import pl.openpayments.ui.theme.Positive

val categoryIcons: Map<String, ImageVector> = linkedMapOf(
    "cart" to Icons.Filled.ShoppingCart,
    "bolt" to Icons.Filled.Bolt,
    "car" to Icons.Filled.DirectionsCar,
    "movie" to Icons.Filled.Movie,
    "food" to Icons.Filled.Restaurant,
    "health" to Icons.Filled.LocalHospital,
    "home" to Icons.Filled.Home,
    "shirt" to Icons.Filled.Checkroom,
    "repeat" to Icons.Filled.Autorenew,
    "dots" to Icons.Filled.MoreHoriz,
    "wallet" to Icons.Filled.AccountBalanceWallet,
    "plus" to Icons.Filled.AddCircle,
    "pets" to Icons.Filled.Pets,
    "kids" to Icons.Filled.ChildCare,
    "school" to Icons.Filled.School,
    "gift" to Icons.Filled.CardGiftcard,
    "travel" to Icons.Filled.Flight,
    "sport" to Icons.Filled.FitnessCenter,
    "phone" to Icons.Filled.PhoneAndroid,
    "coffee" to Icons.Filled.LocalCafe,
)

val categoryPalette = listOf(
    0xFF2E8B6E, 0xFF3B6FB6, 0xFFE08A2E, 0xFF9B59B6, 0xFFD9534F, 0xFF16A2B8,
    0xFF8D6E63, 0xFFC2185B, 0xFF5C6BC0, 0xFF78909C, 0xFF43A047, 0xFFB8860B,
)

fun iconFor(key: String?): ImageVector = categoryIcons[key] ?: Icons.Filled.MoreHoriz

@Composable
fun CategoryBadge(category: Category?, size: Dp = 42.dp) {
    val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconFor(category?.icon), null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) }
    }
}

@Composable
fun TransactionRow(t: Transaction, category: Category?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category?.name ?: "Bez kategorii", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                when (t.source) {
                    TxSource.RECEIPT -> SourceIcon(Icons.AutoMirrored.Filled.ReceiptLong)
                    TxSource.RECURRING -> SourceIcon(Icons.Filled.Autorenew)
                    else -> {}
                }
            }
        }
        Text(
            (if (t.isIncome) "+" else "−") + formatMoney(t.amount),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            color = if (t.isIncome) Positive else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SourceIcon(v: ImageVector) {
    Spacer(Modifier.width(6.dp))
    Icon(v, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Pasek postępu budżetu: zielony → bursztynowy (próg ostrzeżenia) → czerwony (przekroczenie). */
@Composable
fun BudgetBar(spent: Long, limit: Long, alertPercent: Int = 80, height: Dp = 10.dp) {
    val ratio = if (limit > 0) spent.toFloat() / limit else 0f
    val anim by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(700), label = "budget")
    val color = when {
        ratio >= 1f -> Danger
        ratio * 100 >= alertPercent -> pl.openpayments.ui.theme.Amber
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.fillMaxWidth().height(height).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(CircleShape).background(color))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = textColor,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Wybór kategorii w siatce. */
@Composable
fun CategoryPicker(categories: List<Category>, selected: Long?, onSelect: (Long) -> Unit) {
    val rows = categories.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val sel = c.id == selected
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                            .background(if (sel) Color(c.color).copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onSelect(c.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CategoryBadge(c, 38.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
