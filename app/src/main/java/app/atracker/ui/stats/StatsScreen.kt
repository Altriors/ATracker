package app.atracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.LibraryStats
import app.atracker.data.Ranked
import app.atracker.data.TypeStats
import app.atracker.data.activityTotals
import app.atracker.data.computeLibraryStats
import app.atracker.data.db.HistoryRow
import app.atracker.domain.ActivityType
import app.atracker.domain.MediaType
import app.atracker.domain.ProgressUnit
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.formatDate
import app.atracker.ui.common.vm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Period(val label: String) { DAY("Today"), WEEK("This week"), MONTH("This month"), YEAR("This year"), ALL("All time") }

fun periodRange(p: Period, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val start = when (p) {
        Period.DAY -> today
        Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> today.withDayOfMonth(1)
        Period.YEAR -> today.withDayOfYear(1)
        Period.ALL -> LocalDate.of(1970, 1, 2)
    }
    val end = when (p) {
        Period.DAY -> today.plusDays(1)
        Period.WEEK -> start.plusWeeks(1)
        Period.MONTH -> start.plusMonths(1)
        Period.YEAR -> start.plusYears(1)
        Period.ALL -> today.plusYears(100)
    }
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
}

class StatsViewModel(private val app: ATrackerApp) : ViewModel() {
    private val db = app.database
    val period = MutableStateFlow(Period.WEEK)

    val stats: StateFlow<LibraryStats?> = combine(
        db.libraryDao().observeAll(), db.mediaDao().observeAllGenres(), db.mediaDao().observeAllCredits()
    ) { items, genres, credits ->
        computeLibraryStats(
            items,
            genres.filter { it.kind == "GENRE" }.groupBy({ it.mediaId }, { it.name }),
            credits.groupBy { it.mediaId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<HistoryRow>> = period.flatMapLatest { p ->
        val (a, b) = periodRange(p)
        db.activityDao().observeHistory(a, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals: StateFlow<Map<String, Int>> = history.map { activityTotals(it.map { r -> r.event }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Voiding hides an event from statistics. It never changes the current progress. */
    fun toggleVoid(row: HistoryRow) {
        viewModelScope.launch { app.libraryRepository.voidEvent(row.event.id, row.event.voidedAt == null) }
    }
}

@Composable
fun StatsScreen(app: ATrackerApp, onOpenTitle: (Long) -> Unit) {
    val model = vm { StatsViewModel(app) }
    val stats by model.stats.collectAsState()
    val period by model.period.collectAsState()
    val history by model.history.collectAsState()
    val totals by model.totals.collectAsState()
    val s = stats
    if (s == null || s.totalTitles == 0) {
        EmptyState("No statistics yet", "Add titles to your library and track some progress. Statistics are calculated from your own data, per format.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "${s.totalTitles} titles · ${s.favorites} favourites" + (s.averageRating?.let { " · average rating ${fmt(it)}/10" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item { StatusBars(s) }
        items(s.perType, key = { it.type.name }) { TypeCard(it) }
        item { RankCard("Favourite genres", s.topGenres) }
        item { RankCard("Most-read authors", s.topAuthors) }
        item { RankCard("Studios you watch most", s.topStudios) }
        item { Text("Activity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 4.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries.toList()) { p -> FilterChip(period == p, { model.period.value = p }, { Text(p.label) }) }
            }
        }
        item {
            val text = if (totals.isEmpty()) "No progress logged in this period." else totals.entries.joinToString(" · ") { (u, n) ->
                val unit = ProgressUnit.valueOf(u)
                "$n ${if (n == 1) unit.singular else unit.plural}"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp, 8.dp))
            Text(
                "Episodes, chapters and volumes are listed separately and never added together.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(history, key = { it.event.id }) { row -> HistoryItem(row, onOpenTitle) { model.toggleVoid(row) } }
    }
}

private fun fmt(rating: Double) = String.format(Locale.ROOT, "%.1f", rating / 10.0)

@Composable
private fun StatusBars(s: LibraryStats) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(16.dp)) {
        s.perType.forEach { t ->
            Text(t.type.label, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().height(10.dp).padding(vertical = 1.dp).clip(RoundedCornerShape(5.dp)).background(colors.surfaceVariant)) {
                val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.error, colors.outline)
                t.byStatus.entries.forEachIndexed { i, (_, n) ->
                    Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(palette[i % palette.size]))
                }
            }
            Text(
                t.byStatus.entries.joinToString(" · ") { (st, n) -> "$n ${st.label(t.type).lowercase()}" },
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun TypeCard(t: TypeStats) {
    Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(t.type.label, style = MaterialTheme.typography.titleMedium)
            val verb = if (t.type == MediaType.ANIME) "watched" else "read"
            Text("${t.titles} titles · ${t.completed} completed", style = MaterialTheme.typography.bodyMedium)
            Text("${t.progress} ${if (t.progress == 1) t.unit.singular else t.unit.plural} $verb", style = MaterialTheme.typography.bodyMedium)
            if (t.volumes > 0 && t.unit != ProgressUnit.VOLUME) Text("${t.volumes} volumes read", style = MaterialTheme.typography.bodyMedium)
            t.averageRating?.let { Text("Average rating ${fmt(it)}/10 (${t.ratedCount} rated)", style = MaterialTheme.typography.bodyMedium) }
            t.watchMinutes?.takeIf { it > 0 }?.let { m ->
                val (known, all) = t.watchMinutesCoverage ?: (0 to 0)
                Text(
                    "Estimated watch time ${m / 60} h ${m % 60} min" + if (known < all) " (episode length known for $known of $all titles)" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RankCard(title: String, items: List<Ranked>) {
    if (items.isEmpty()) return
    Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            val max = items.maxOf { it.count }.toFloat()
            items.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Box(Modifier.fillMaxWidth(r.count / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                    Text("${r.count}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, onOpen: (Long) -> Unit, onToggleVoid: () -> Unit) {
    val e = row.event
    val voided = e.voidedAt != null
    val desc = when (e.type) {
        ActivityType.PROGRESS.name, ActivityType.VOLUME_PROGRESS.name -> "${e.fromValue ?: 0} → ${e.toValue ?: 0} ${e.unit.lowercase()}s"
        else -> e.type.lowercase().replace('_', ' ') + (e.toValue?.let { " ($it)" } ?: "")
    }
    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TextButton(onClick = { onOpen(e.mediaId) }, contentPadding = PaddingValues(0.dp)) {
                Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatDate(e.timestamp) + " · " + desc + if (voided) " · voided" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (e.type == ActivityType.PROGRESS.name || e.type == ActivityType.VOLUME_PROGRESS.name) {
            TextButton(onClick = onToggleVoid) { Text(if (voided) "Restore" else "Void") }
        }
    }
}
