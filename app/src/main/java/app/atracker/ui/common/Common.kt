package app.atracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.atracker.data.db.MediaEntity
import app.atracker.data.trackedTotal
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit
import app.atracker.domain.PublicationStatus
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Creates a ViewModel with constructor arguments without needing a factory class per screen. */
@Composable
inline fun <reified T : ViewModel> vm(key: String? = null, crossinline create: () -> T): T =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    })

/** Cover art with a neutral placeholder that stays visible when the image is missing or fails to load. */
@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

fun ratingText(rating: Int?): String = if (rating == null) "—" else String.format(Locale.ROOT, "%.1f", rating / 10.0)

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
fun formatDate(millis: Long?): String =
    if (millis == null) "—" else dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun progressText(progress: Int, total: Int?, unit: ProgressUnit): String {
    val u = if (progress == 1 && total == null) unit.singular else unit.plural
    return if (total != null) "$progress / $total $u" else "$progress / ? $u"
}

fun MediaEntity.subtitle(): String = buildList {
    add(MediaType.from(mediaType).label)
    startYear?.let { add(it.toString()) }
    pubStatus.takeIf { it != "UNKNOWN" }?.let { add(PublicationStatus.from(it).label) }
}.joinToString(" · ")

fun MediaMetadata.subtitle(): String = buildList {
    add(mediaType.label)
    startYear?.let { add(it.toString()) }
    pubStatus.takeIf { it != PublicationStatus.UNKNOWN }?.let { add(it.label) }
}.joinToString(" · ")

fun MediaEntity.totalLabel(): String {
    val unit = MediaType.from(mediaType).progressUnit
    val total = trackedTotal()
    return if (total != null) "$total ${unit.plural}" else "unknown number of ${unit.plural}"
}

/** One row used by search results, library list and lists on the home screen. */
@Composable
fun MediaRow(
    title: String, subtitle: String, coverUrl: String?, modifier: Modifier = Modifier,
    badge: String? = null, extra: String? = null, trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverUrl, Modifier.size(width = 56.dp, height = 80.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
    }
}

/** A text button that opens a dropdown of choices. [null] option means "any". */
@Composable
fun <T> Picker(
    label: String, selected: T?, options: List<T>, display: (T) -> String,
    onSelect: (T?) -> Unit, anyLabel: String? = "Any", modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text(if (selected == null) label else "$label: ${display(selected)}", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (anyLabel != null) DropdownMenuItem(text = { Text(anyLabel) }, onClick = { open = false; onSelect(null) })
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { open = false; onSelect(o) })
            }
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

fun LibraryStatus.chipLabel(type: MediaType?): String = if (type == null) when (this) {
    LibraryStatus.CURRENT -> "Watching/Reading"
    LibraryStatus.PLANNED -> "Planned"
    else -> label(MediaType.MANGA)
} else label(type)
