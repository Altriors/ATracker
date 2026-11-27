package app.atracker.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.db.CustomListEntity
import app.atracker.data.db.CustomListItemEntity
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.MediaRow
import app.atracker.ui.common.subtitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(app: ATrackerApp, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val dao = app.database.listDao()
    val lists by dao.observeSummaries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Custom lists") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New list") } }
    ) { padding ->
        if (lists.isEmpty()) {
            EmptyState("No lists yet", "Create a list such as “Cultivation novels” or “To rewatch”, then add titles from their pages.", Modifier.padding(padding))
        } else LazyColumn(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(lists, key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(l.id) }) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(l.name, style = MaterialTheme.typography.titleMedium); Text("${l.count} titles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { renaming = l.id to l.name }) { Icon(Icons.Filled.Edit, "Rename ${l.name}") }
                        IconButton(onClick = { deleting = l.id to l.name }) { Icon(Icons.Filled.Delete, "Delete ${l.name}") }
                    }
                }
            }
        }
    }
    if (creating) NameDialog("New list", "", { creating = false }) { name -> scope.launch { dao.insertList(CustomListEntity(name = name, createdAt = System.currentTimeMillis())) }; creating = false }
    renaming?.let { (id, old) -> NameDialog("Rename list", old, { renaming = null }) { name -> scope.launch { dao.renameList(id, name) }; renaming = null } }
    deleting?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete “$name”?") },
            text = { Text("Only the list is deleted. The titles stay in your library.") },
            confirmButton = { TextButton(onClick = { scope.launch { dao.deleteList(CustomListEntity(id = id, name = name, createdAt = 0)) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(40) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(app: ATrackerApp, listId: Long, onBack: () -> Unit, onOpenTitle: (Long) -> Unit) {
    val dao = app.database.listDao()
    val list by dao.observeList(listId).collectAsState(initial = null)
    val items by dao.observeMedia(listId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(list?.name ?: "List") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        if (items.isEmpty()) EmptyState("This list is empty", "Open a title and use “Add to a list” in its top bar.", Modifier.padding(padding))
        else LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(items, key = { it.id }) { m ->
                MediaRow(m.title, m.subtitle(), m.coverUrl, trailing = {
                    IconButton(onClick = { scope.launch { dao.removeItem(listId, m.id) } }) { Icon(Icons.Filled.RemoveCircleOutline, "Remove ${m.title} from list") }
                }) { onOpenTitle(m.id) }
            }
        }
    }
}

/** Tick the lists this title belongs to. Changes apply immediately. */
@Composable
fun AddToListDialog(app: ATrackerApp, mediaId: Long, onDismiss: () -> Unit) {
    val dao = app.database.listDao()
    val lists by dao.observeSummaries().collectAsState(initial = emptyList())
    val member by dao.observeListIdsFor(mediaId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add to a list") },
        text = {
            if (lists.isEmpty()) Text("You have no lists yet. Create one from the profile menu → Custom lists.")
            else Column {
                lists.forEach { l ->
                    val checked = l.id in member
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.launch { if (checked) dao.removeItem(l.id, mediaId) else dao.addItem(CustomListItemEntity(l.id, mediaId, 0, System.currentTimeMillis())) }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = null); Text(l.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
