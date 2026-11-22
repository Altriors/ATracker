package app.atracker.ui.franchise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.FranchiseStats
import app.atracker.data.computeFranchiseStats
import app.atracker.data.db.FranchiseEntity
import app.atracker.data.db.FranchiseMemberRow
import app.atracker.data.db.FranchiseSummary
import app.atracker.data.trackedTotal
import app.atracker.domain.FranchiseRelation
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import app.atracker.ui.common.Cover
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.Picker
import app.atracker.ui.common.progressText
import app.atracker.ui.common.ratingText
import app.atracker.ui.common.vm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class FranchiseViewModel(private val app: ATrackerApp, val franchiseId: Long) : ViewModel() {
    private val dao = app.database.franchiseDao()
    val franchise: StateFlow<FranchiseEntity?> = dao.observeFranchise(franchiseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val members: StateFlow<List<FranchiseMemberRow>> = dao.observeMembers(franchiseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats: StateFlow<FranchiseStats?> = dao.observeMembers(franchiseId)
        .map { rows -> if (rows.isEmpty()) null else computeFranchiseStats(rows.map { it.media to it.entry }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) { viewModelScope.launch { app.franchiseRepository.rename(franchiseId, name) } }
    fun setRelation(mediaId: Long, r: FranchiseRelation) { viewModelScope.launch { app.franchiseRepository.setRelation(mediaId, r) } }
    fun unlink(mediaId: Long) { viewModelScope.launch { app.franchiseRepository.unlink(mediaId) } }
}

class FranchiseListViewModel(app: ATrackerApp) : ViewModel() {
    val items: StateFlow<List<FranchiseSummary>> = app.database.franchiseDao().observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FranchiseListScreen(app: ATrackerApp, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val model = vm { FranchiseListViewModel(app) }
    val items by model.items.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Franchises") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                "No franchises yet",
                "Open a title and use “Link another title…” to connect related adaptations, such as a web novel, its manhua and its anime. Each keeps its own progress.",
                Modifier.padding(padding)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(items, key = { it.franchise.id }) { s ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(s.franchise.id) }.padding(16.dp, 10.dp)) {
                        Text(s.franchise.name, style = MaterialTheme.typography.titleMedium)
                        Text("${s.memberCount} titles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FranchiseScreen(app: ATrackerApp, franchiseId: Long, onBack: () -> Unit, onOpenTitle: (Long) -> Unit) {
    val model = vm(key = "franchise-$franchiseId") { FranchiseViewModel(app, franchiseId) }
    val franchise by model.franchise.collectAsState()
    val members by model.members.collectAsState()
    val stats by model.stats.collectAsState()
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(franchise?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Rename franchise") } }
            )
        }
    ) { padding ->
        val sources = members.filter { it.member.relation == FranchiseRelation.SOURCE.name }
        val others = members.filter { it.member.relation != FranchiseRelation.SOURCE.name }.sortedBy { it.media.mediaType }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { StatsCard(stats) }
            if (sources.isNotEmpty()) {
                item { Header("Original source") }
                items(sources, key = { "s${it.media.id}" }) { MemberCard(it, model, onOpenTitle) }
            }
            item { Header(if (sources.isEmpty()) "Titles in this franchise" else "Adaptations and related titles") }
            items(others, key = { "o${it.media.id}" }) { MemberCard(it, model, onOpenTitle) }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(franchise?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename franchise") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = false; model.rename(text) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp))
}

@Composable
private fun StatsCard(stats: FranchiseStats?) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Combined statistics", style = MaterialTheme.typography.titleMedium)
            if (stats == null) {
                Text("No titles yet.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(
                "${stats.titles} titles · ${stats.inLibrary} in your library · ${stats.completed} completed" +
                    (stats.averageRating?.let { " · average rating ${ratingText(it.toInt())}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            stats.formats.forEach { f ->
                val verb = if (f.type == MediaType.ANIME) "watched" else "read"
                val unit = if (f.progress == 1) f.unit.singular else f.unit.plural
                Text(
                    "${f.type.label}: ${f.titles} ${if (f.titles == 1) "title" else "titles"} · ${f.progress} $unit $verb" +
                        (if (f.volumes > 0) " · ${f.volumes} volumes" else "") +
                        (f.averageRating?.let { " · avg ${String.format(Locale.ROOT, "%.1f", it / 10.0)}/10" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Each format is counted in its own unit. Episodes, chapters and volumes are never added together.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemberCard(row: FranchiseMemberRow, model: FranchiseViewModel, onOpen: (Long) -> Unit) {
    val type = MediaType.from(row.media.mediaType)
    val relation = FranchiseRelation.valueOf(row.member.relation)
    var confirm by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(16.dp, 4.dp).clickable { onOpen(row.media.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp)) {
            Cover(row.media.coverUrl, Modifier.size(width = 64.dp, height = 92.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.media.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(type.label + " · " + relation.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                val e = row.entry
                if (e == null) {
                    Text("Not in your library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(LibraryStatus.from(e.status).label(type) + " · " + progressText(e.progress, row.media.trackedTotal(), type.progressUnit), style = MaterialTheme.typography.bodySmall)
                    Text("Your rating: ${ratingText(e.rating)}${if (e.rating != null) " / 10" else ""}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Picker("Role", relation, FranchiseRelation.entries.toList(), { it.label }, { r -> r?.let { model.setRelation(row.media.id, it) } }, anyLabel = null)
                    TextButton(onClick = { confirm = true }) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Unlink ${row.media.title}?") },
            text = { Text("It leaves this franchise. Its own progress, rating and history stay untouched.") },
            confirmButton = { TextButton(onClick = { confirm = false; model.unlink(row.media.id) }) { Text("Unlink") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
