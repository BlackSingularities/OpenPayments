package pl.openpayments.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.openpayments.data.Category
import pl.openpayments.data.Transaction
import pl.openpayments.data.TxSource
import pl.openpayments.domain.formatMoney
import pl.openpayments.ui.theme.Amber
import pl.openpayments.ui.theme.Danger
import pl.openpayments.ui.theme.Positive
import pl.openpayments.ui.theme.isDark

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

/** Każda ikona kategorii ma własny kształt Expressive — lista łatwiej się „czyta” wzrokiem. */
private val badgeShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided,
    MaterialShapes.SoftBurst, MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.Cookie4Sided,
    MaterialShapes.Flower, MaterialShapes.Puffy, MaterialShapes.Arch, MaterialShapes.Bun,
)

@Composable
fun shapeFor(key: String?): Shape {
    val polygon = badgeShapes[(categoryIcons.keys.indexOf(key ?: "dots").coerceAtLeast(0)) % badgeShapes.size]
    return polygon.toShape()
}

/** Kolor kategorii dopasowany do jasnego/ciemnego motywu (tło tonalne + wyraźna ikona). */
@Composable
fun categoryColors(category: Category?): Pair<Color, Color> {
    val base = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    val dark = isDark()
    val container = if (dark) lerp(base, Color.Black, 0.4f) else lerp(base, Color.White, 0.78f)
    val content = if (dark) lerp(base, Color.White, 0.6f) else lerp(base, Color.Black, 0.25f)
    return container to content
}

@Composable
fun CategoryBadge(category: Category?, size: Dp = 44.dp) {
    val (container, content) = categoryColors(category)
    Box(
        Modifier.size(size).clip(shapeFor(category?.icon)).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(iconFor(category?.icon), null, tint = content, modifier = Modifier.size(size * 0.48f)) }
}

/** Ekran z dużym, zwijanym paskiem tytułu (Android 16). */
@Composable
fun ExpressiveScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle?.let { { Text(it, maxLines = 1) } },
                navigationIcon = {
                    if (onBack != null) FilledTonalIconButton(onClick = onBack, Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                    }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content,
    )
}

/** Karta sekcji na tle surfaceContainer — bez obramowań i cieni. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** Nagłówek grupy w stylu ustawień Androida 16 (mały, w kolorze primary). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/**
 * Zgrupowana lista: elementy rozdzielone szczeliną 2 dp, pierwszy i ostatni mocniej zaokrągleni
 * — wygląd list z ustawień Androida 16.
 */
@Composable
fun <T> SegmentedGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, shapes: ListItemShapes) -> Unit,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item -> row(item, ListItemDefaults.segmentedShapes(i, items.size)) }
    }
}

@Composable
fun segmentedColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** Wiersz grupy: tytuł, opis, ikona i opcjonalny element po prawej. */
@Composable
fun GroupRow(
    shapes: ListItemShapes,
    title: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val lead: (@Composable () -> Unit)? = leading ?: icon?.let { v -> { RowIcon(v) } }
    val support: (@Composable () -> Unit)? = supporting?.let { s -> { Text(s) } }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick, shapes = shapes, enabled = enabled, colors = segmentedColors(),
            leadingContent = lead, trailingContent = trailing, supportingContent = support,
        ) { Text(title) }
    } else {
        StaticSegmentedItem(shapes, leadingContent = lead, trailingContent = trailing, supportingContent = support) { Text(title) }
    }
}

/** Nieklikalny element zgrupowanej listy (ten sam wygląd co SegmentedListItem). */
@Composable
fun StaticSegmentedItem(
    shapes: ListItemShapes,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = headline, leadingContent = leadingContent, trailingContent = trailingContent,
        supportingContent = supportingContent,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(shapes.shape),
    )
}

@Composable
private fun RowIcon(v: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(v, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
}

/** Kwota transakcji: przychód na zielono ze znakiem „+”. */
@Composable
fun AmountText(t: Transaction, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) {
    Text(
        (if (t.isIncome) "+" else "−") + formatMoney(t.amount), style = style,
        color = if (t.isIncome) Positive else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun TransactionItem(t: Transaction, category: Category?, shapes: ListItemShapes, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick, shapes = shapes, colors = segmentedColors(),
        leadingContent = { CategoryBadge(category) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category?.name ?: "Bez kategorii", maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (t.source) {
                    TxSource.RECEIPT -> SourceIcon(Icons.AutoMirrored.Filled.ReceiptLong)
                    TxSource.RECURRING -> SourceIcon(Icons.Filled.Autorenew)
                    else -> {}
                }
            }
        },
        trailingContent = { AmountText(t) },
    ) { Text(t.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun SourceIcon(v: ImageVector) {
    Spacer(Modifier.width(6.dp))
    Icon(v, null, Modifier.size(14.dp))
}

/** Postęp budżetu: falująca linia (Expressive); kolor zmienia się po progu i po przekroczeniu. */
@Composable
fun BudgetBar(spent: Long, limit: Long, alertPercent: Int = 80, modifier: Modifier = Modifier, onColor: Boolean = false) {
    val ratio = if (limit > 0) spent.toFloat() / limit else 0f
    val anim by animateFloatAsState(ratio.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "budget")
    val scheme = MaterialTheme.colorScheme
    val color = when {
        ratio >= 1f -> if (onColor) Color(0xFFFFB4AB) else Danger
        ratio * 100 >= alertPercent -> Amber
        onColor -> scheme.onPrimary
        else -> scheme.primary
    }
    val track = if (onColor) scheme.onPrimary.copy(alpha = 0.25f) else scheme.surfaceContainerHighest
    LinearWavyProgressIndicator(
        progress = { anim }, modifier = modifier.fillMaxWidth().height(14.dp),
        color = color, trackColor = track,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = MaterialShapes.Cookie9Sided.toShape()
        Box(
            Modifier.size(96.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = content,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Połączona grupa przełączników (Android 16) — zamiennik segmented buttons. */
@Composable
fun <T> ConnectedToggleGroup(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = value == selected, onCheckedChange = { onSelect(value) },
                modifier = Modifier.weight(1f),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** Pole tekstowe wypełnione tonem powierzchni, bez dolnej linii (Android 16). */
@Composable
fun filledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** Wybór kategorii w siatce kształtów Expressive. */
@Composable
fun CategoryPicker(categories: List<Category>, selected: Long?, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    val sel = c.id == selected
                    val scale by animateFloatAsState(if (sel) 1.12f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sel")
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onSelect(c.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { CategoryBadge(c, 42.dp) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
