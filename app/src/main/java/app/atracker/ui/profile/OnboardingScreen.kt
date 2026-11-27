package app.atracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atracker.ATrackerApp
import app.atracker.data.db.AppSettingEntity
import app.atracker.ui.settings.KEY_THEME
import kotlinx.coroutines.launch

const val KEY_ONBOARDED = "onboarded"

/** First-run setup: name, theme, then either start empty or go straight to the MyAnimeList import. */
@Composable
fun OnboardingScreen(app: ATrackerApp, theme: String, onFinished: (openMal: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val dao = app.database.settingDao()
    fun finish(mal: Boolean) {
        scope.launch {
            if (name.isNotBlank()) dao.put(AppSettingEntity(KEY_PROFILE_NAME, name.trim()))
            dao.put(AppSettingEntity(KEY_ONBOARDED, "yes"))
            onFinished(mal)
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Welcome to ATracker", style = MaterialTheme.typography.headlineMedium)
        Text("Your private tracker for anime, manga, manhwa, manhua, light novels and web novels. Everything stays on this phone. No account needed.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                FilterChip(theme == v, { scope.launch { dao.put(AppSettingEntity(KEY_THEME, v)) } }, { Text(label) })
            }
        }
        Text("Already tracking on MyAnimeList? Import your lists now so you don't re-enter anything. You always see a preview first.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { finish(true) }, Modifier.fillMaxWidth()) { Text("Continue and import from MyAnimeList") }
        OutlinedButton(onClick = { finish(false) }, Modifier.fillMaxWidth()) { Text("Start with an empty library") }
        Text("You can change all of this later in Settings and Profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
