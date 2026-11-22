package app.atracker.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atracker.data.db.MediaEntity
import app.atracker.data.trackedTotal
import app.atracker.domain.FranchiseRelation
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaMetadata
import app.atracker.domain.MediaType
import app.atracker.ui.common.Cover
import app.atracker.ui.common.Picker
import app.atracker.ui.common.progressText
import app.atracker.ui.common.subtitle
import kotlinx.coroutines.launch

@Composable
fun FranchiseSection(model: DetailViewModel, onOpenTitle: (Long) -> Unit, onOpenFranchise: (Long) -> Unit) {
    val franchise by model.franchise.collectAsState()
    val members by model.members.collectAsState()
    val suggestions by model.suggestions.collectAsState()
    var showLink by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Franchise", style = MaterialTheme.typography.titleMedium)
            val f = franchise
            if (f != null) {
                Text(f.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Each linked title keeps its own progress, rating and history.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                members.filter { it.media.id != model.mediaId }.forEach { row ->
                    val type = MediaType.from(row.media.mediaType)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenTitle(row.media.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(row.media.coverUrl, Modifier.size(width = 36.dp, height = 52.dp), corner = 6.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.media.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                type.label + " · " + FranchiseRelation.valueOf(row.member.relation).label +
                                    (row.entry?.let { " · " + LibraryStatus.from(it.status).label(type) + " · " + progressText(it.progress, row.media.trackedTotal(), type.progressUnit) } ?: " · not in your library"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row {
                    TextButton(onClick = { onOpenFranchise(f.id) }) { Text("Open franchise page") }
                    TextButton(onClick = { confirmUnlink = true }) { Text("Unlink this title", color = MaterialTheme.colorScheme.error) }
                }
            } else {
                Text(
                    "Not linked. Linking connects related adaptations (for example a novel and its anime) while keeping separate progress for each.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val open = suggestions.filter { !it.alreadyInFranchise }
            if (open.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Suggested by the provider", style = MaterialTheme.typography.titleSmall)
                Text("Nothing is linked unless you tap Link.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                open.take(10).forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.relation.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                FranchiseRelation.valueOf(s.relation.relation).label + " · " + (s.relation.relatedType?.let { MediaType.from(it).label } ?: "Unknown format"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { model.openSuggestion(s, onOpenTitle) }) { Text("View") }
                        TextButton(onClick = { model.linkSuggestion(s) }) { Text("Link") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showLink = true }) { Text("Link another title…") }
        }
    }

    if (showLink) LinkDialog(model, onDismiss = { showLink = false })
    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("Unlink this title?") },
            text = { Text("It leaves the franchise. Its progress, rating and history are not touched.") },
            confirmButton = { TextButton(onClick = { confirmUnlink = false; model.unlinkFromFranchise() }) { Text("Unlink") } },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancel") } }
        )
    }
}

/** Pick another title (saved on this device, or found online) and say how it relates to this one. */
@Composable
private fun LinkDialog(model: DetailViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<List<MediaEntity>>(emptyList()) }
    var online by remember { mutableStateOf<List<MediaMetadata>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var relation by remember { mutableStateOf(FranchiseRelation.ADAPTATION) }

    LaunchedEffect(query) { local = model.searchLocal(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link another title") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(query, { query = it }, label = { Text("Search by title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("The other title is a(n)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Picker("", relation, FranchiseRelation.entries.toList(), { it.label }, { it?.let { r -> relation = r } }, anyLabel = null)
                }
                if (local.isNotEmpty()) {
                    Text("On this device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    local.forEach { m ->
                        Column(Modifier.fillMaxWidth().clickable { model.linkWith(m.id, relation); onDismiss() }.padding(vertical = 6.dp)) {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.subtitle(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (query.isNotBlank()) {
                    TextButton(onClick = {
                        scope.launch {
                            searching = true; onlineError = null
                            try {
                                val out = model.searchOnline(query)
                                online = out.remote
                                onlineError = out.errors.values.firstOrNull()
                            } catch (e: Exception) { onlineError = e.message }
                            searching = false
                        }
                    }) { Text("Search online too") }
                }
                if (searching) CircularProgressIndicator(Modifier.size(24.dp))
                onlineError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (online.isNotEmpty()) {
                    Text("Online", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    online.take(15).forEach { m ->
                        Column(Modifier.fillMaxWidth().clickable { model.linkWithOnline(m, relation); onDismiss() }.padding(vertical = 6.dp)) {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.subtitle(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Titles are never linked just because their names are similar. Picking one here links it with this title only.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
