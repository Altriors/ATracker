package app.atracker.ui.mal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.mal.ConflictPolicy
import app.atracker.data.mal.MalAuth
import app.atracker.data.mal.PlanAction
import app.atracker.ui.common.formatDate
import app.atracker.ui.common.vm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalImportScreen(app: ATrackerApp, onBack: () -> Unit) {
    val model = vm { MalImportViewModel(app) }
    val ui by model.ui.collectAsState()
    val connected by model.connected.collectAsState()
    val savedId by model.clientId.collectAsState()
    val runs by model.runs.collectAsState()
    val opened by model.records.collectAsState()
    val redirect by app.pendingMalRedirect.collectAsState()
    val context = LocalContext.current
    var clientId by remember(savedId) { mutableStateOf(savedId.orEmpty()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) model.importFile(context, uri) }

    LaunchedEffect(redirect) { redirect?.let { model.handleRedirect(it); app.pendingMalRedirect.value = null } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("MyAnimeList import") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = ui) {
                is ImportUi.Working -> item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(); Text(s.message)
                        }
                    }
                }
                is ImportUi.Preview -> {
                    val plan = s.plan
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Preview: nothing has been changed yet", style = MaterialTheme.typography.titleMedium)
                                Text("${plan.items.size} entries read from MyAnimeList")
                                PlanAction.entries.forEach { a -> val n = plan.count(a); if (n > 0) Text("• $n ${a.label.lowercase()}", style = MaterialTheme.typography.bodyMedium) }
                                plan.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("If a title is already in your library", style = MaterialTheme.typography.titleSmall)
                                ConflictPolicy.entries.forEach { p ->
                                    Row(Modifier.fillMaxWidth().selectable(selected = s.policy == p, role = Role.RadioButton, onClick = { model.setPolicy(p) }).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                        RadioButton(selected = s.policy == p, onClick = null)
                                        Column(Modifier.padding(start = 12.dp)) { Text(p.label); Text(p.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = model::confirm, modifier = Modifier.weight(1f)) { Text("Import ${plan.items.count { !it.skip }} titles") }
                            OutlinedButton(onClick = model::reset) { Text("Cancel") }
                        }
                    }
                    if (plan.count(PlanAction.SAME) > 0) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Skip the ${plan.count(PlanAction.SAME)} identical titles", Modifier.weight(1f))
                            Switch(checked = plan.items.filter { it.action == PlanAction.SAME }.all { it.skip }, onCheckedChange = { model.skipAll(PlanAction.SAME, it) })
                        }
                    }
                    itemsIndexed(plan.items) { index, it ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(it.record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                    Text("${it.record.kind.key.replaceFirstChar { c -> c.uppercase() }} · ${it.record.status.label(app.atracker.domain.MediaType.ANIME)} · ${it.record.progress}" + (it.record.total?.let { t -> " / $t" } ?: "") + (it.record.score?.let { sc -> " · score $sc" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(it.action.label + (it.meta?.let { m -> " (${m.provider}: ${m.title})" } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    if (it.differences.isNotEmpty()) Text(it.differences.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Skip", style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = it.skip, onCheckedChange = { v -> model.setSkip(index, v) })
                                }
                            }
                            if (it.action == PlanAction.POSSIBLE_DUPLICATE) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Same title already in the app: link to it", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Switch(checked = it.linkToMediaId != null, onCheckedChange = { v -> model.setLink(index, v) })
                            }
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
                is ImportUi.Done -> item {
                    val r = s.report
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Import finished", style = MaterialTheme.typography.titleMedium)
                            Text("${r.added} added" + (if (r.unmatched > 0) " (${r.unmatched} with MAL data only, no online match)" else ""))
                            Text("${r.updated} updated · ${r.skipped} skipped · ${r.failed} failed")
                            Text("Details are saved in the import history below. Running it again is safe.", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = model::reset) { Text("Done") }
                        }
                    }
                }
                else -> {
                    if (s is ImportUi.Error) item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text("That did not work", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            SelectionContainer { Text(s.message) }
                            TextButton(onClick = model::reset) { Text("Dismiss") }
                        } }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Import an export file", style = MaterialTheme.typography.titleMedium)
                            Text("Works without any setup. On myanimelist.net open Profile → Export My List, download the anime and manga files, and pick each one here (.xml or .xml.gz). Run it once per file.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose export file") }
                        } }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Connect your MyAnimeList account", style = MaterialTheme.typography.titleMedium)
                            Text("MyAnimeList requires each app to have a free Client ID. Create one at myanimelist.net/apiconfig (App Type: other) with this redirect URL, then paste the Client ID below:", style = MaterialTheme.typography.bodyMedium)
                            SelectionContainer { Text(MalAuth.REDIRECT_URI, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                            OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { model.saveClientId(clientId) }, enabled = clientId.trim() != savedId.orEmpty()) { Text("Save") }
                                if (connected) {
                                    Button(onClick = model::fetchFromMal) { Text("Import my lists") }
                                    TextButton(onClick = model::disconnect) { Text("Disconnect") }
                                } else {
                                    Button(onClick = { model.saveClientId(clientId); model.connect(context) }, enabled = clientId.isNotBlank()) { Text("Connect") }
                                }
                            }
                            Text(if (connected) "Connected. The sign-in token is stored encrypted on this phone." else "Not connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                    }
                    item { Text("Import history", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    if (runs.isEmpty()) item { Text("No imports yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    itemsIndexed(runs) { _, run ->
                        Card(Modifier.fillMaxWidth().selectable(selected = false, role = Role.Button, onClick = { model.openRun(run) })) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${if (run.source == "MAL_API") "MyAnimeList account" else "Export file"} · ${formatDate(run.startedAt)} · ${run.status.lowercase()}")
                                Text("${run.added} added · ${run.updated} updated · ${run.skipped} skipped · ${run.unmatched} unmatched · ${run.failed} failed", style = MaterialTheme.typography.bodySmall)
                                run.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }

    opened?.let { (run, recs) ->
        AlertDialog(onDismissRequest = model::closeRun, confirmButton = { TextButton(onClick = model::closeRun) { Text("Close") } },
            title = { Text("Import details") },
            text = {
                LazyColumn { itemsIndexed(recs) { _, r ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${r.title}: ${r.outcome.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                        r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } }
            })
    }
}
