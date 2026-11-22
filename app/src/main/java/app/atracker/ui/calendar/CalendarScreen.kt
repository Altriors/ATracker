package app.atracker.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.db.ReleaseRow
import app.atracker.domain.MediaType
import app.atracker.ui.common.EmptyState
import app.atracker.ui.common.vm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CalendarViewModel(app: ATrackerApp) : ViewModel() {
    private val now = System.currentTimeMillis()
    val releases: StateFlow<List<ReleaseRow>?> =
        app.database.activityDao().observeLibraryReleases(now - 12 * 3_600_000L, now + 60L * 24 * 3_600_000)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(app: ATrackerApp, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val model = vm { CalendarViewModel(app) }
    val rows by model.releases.collectAsState()
    val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Upcoming releases") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        val list = rows.orEmpty()
        if (list.isEmpty()) {
            EmptyState(
                "Nothing scheduled",
                "Only titles in your library with a confirmed upcoming date appear here (airing anime, for example). Dates come from AniList and are shown only when the provider knows them; nothing is guessed.",
                Modifier.padding(padding)
            )
            return@Scaffold
        }
        val zone = ZoneId.systemDefault()
        val byDay = list.groupBy { Instant.ofEpochMilli(it.releaseAt).atZone(zone).toLocalDate() }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            byDay.forEach { (day, rs) ->
                item(key = "d$day") { Text(day.format(dayFmt), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
                items(rs, key = { it.id }) { r ->
                    val what = when (r.kind) {
                        "PREMIERE" -> "Premiere"
                        else -> r.kind.lowercase().replaceFirstChar { it.uppercase() } + (r.number?.let { " $it" } ?: "")
                    }
                    Column(Modifier.fillMaxWidth().clickable { onOpen(r.mediaId) }.padding(16.dp, 6.dp)) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            MediaType.from(r.mediaType).label + " · " + what + " · " + Instant.ofEpochMilli(r.releaseAt).atZone(zone).format(timeFmt),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
