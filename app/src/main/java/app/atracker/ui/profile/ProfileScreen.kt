package app.atracker.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.db.AppSettingEntity
import app.atracker.domain.LibraryStatus
import app.atracker.ui.common.vm
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val KEY_PROFILE_NAME = "profile_name"
const val KEY_PROFILE_PHOTO = "profile_photo_version"

class ProfileViewModel(private val app: ATrackerApp) : ViewModel() {
    private val settings = app.database.settingDao()
    val name: StateFlow<String?> = settings.observe(KEY_PROFILE_NAME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val photoVersion: StateFlow<String?> = settings.observe(KEY_PROFILE_PHOTO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val counts: StateFlow<Counts> = app.database.libraryDao().observeAll().map { items ->
        Counts(
            titles = items.size,
            completed = items.count { it.entry.status == LibraryStatus.COMPLETED.name },
            active = items.count { it.entry.status == LibraryStatus.CURRENT.name },
            favourites = items.count { it.entry.favorite }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Counts())

    data class Counts(val titles: Int = 0, val completed: Int = 0, val active: Int = 0, val favourites: Int = 0)

    fun saveName(v: String) { viewModelScope.launch { settings.put(AppSettingEntity(KEY_PROFILE_NAME, v.trim())) } }

    /** Copies the chosen gallery image into app storage, scaled down, so it survives the picker's permission ending. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
                val bmp = app.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@withContext
                photoFile(app).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
            settings.put(AppSettingEntity(KEY_PROFILE_PHOTO, System.currentTimeMillis().toString()))
        }
    }

    fun removePhoto() { viewModelScope.launch { photoFile(app).delete(); settings.put(AppSettingEntity(KEY_PROFILE_PHOTO, "")) } }

    companion object { fun photoFile(app: ATrackerApp) = File(app.filesDir, "profile.jpg") }
}

/** Round profile picture, or the first letter of the name when no photo is set. */
@Composable
fun Avatar(app: ATrackerApp, size: Dp, modifier: Modifier = Modifier) {
    val settings = app.database.settingDao()
    val name by settings.observe(KEY_PROFILE_NAME).collectAsState(initial = null)
    val version by settings.observe(KEY_PROFILE_PHOTO).collectAsState(initial = null)
    val file = ProfileViewModel.photoFile(app)
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (!version.isNullOrEmpty() && file.exists()) {
            AsyncImage(model = file, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text((name?.firstOrNull() ?: 'A').uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(app: ATrackerApp, onBack: () -> Unit, onSettings: () -> Unit, onMal: () -> Unit, onStats: () -> Unit) {
    val model = vm { ProfileViewModel(app) }
    val name by model.name.collectAsState()
    val counts by model.counts.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) model.setPhoto(uri) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Profile") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(app, 96.dp, Modifier.clickable { picker.launch("image/*") })
                Box(Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Change profile photo", modifier = Modifier.size(18.dp))
                }
            }
            Text(name?.takeIf { it.isNotBlank() } ?: "Your name", style = MaterialTheme.typography.headlineSmall)
            Text("Local profile · stays on this phone · no account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }) { Text("Edit name") }
                OutlinedButton(onClick = model::removePhoto) { Text("Remove photo") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Titles", counts.titles, Modifier.weight(1f)); Stat("Active", counts.active, Modifier.weight(1f))
                Stat("Completed", counts.completed, Modifier.weight(1f)); Stat("Favourites", counts.favourites, Modifier.weight(1f))
            }
            Card(Modifier.fillMaxWidth()) {
                Column {
                    Entry("Statistics", "Per format, never mixed", onStats)
                    Entry("MyAnimeList import", "Account or export file, import history", onMal)
                    Entry("Settings", "Theme, backup, export, sources", onSettings)
                }
            }
            Text("Uninstalling the app deletes this profile and your library. Keep a backup outside the phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false }, title = { Text("Your name") },
            text = { OutlinedTextField(text, { text = it.take(30) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { model.saveName(text); editing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable
private fun Entry(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
