package app.atracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.atracker.ui.theme.ATrackerTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DISCOVER("Discover", Icons.Filled.Explore),
    LIBRARY("My Library", Icons.Filled.LibraryBooks),
    STATS("Statistics", Icons.Filled.InsertChart),
    SETTINGS("Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ATrackerApp
        setContent {
            ATrackerTheme {
                var selected by remember { mutableIntStateOf(0) }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selected == index,
                                    onClick = { selected = index },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        when (Tab.entries[selected]) {
                            Tab.HOME -> FoundationScreen(app)
                            else -> Text(Tab.entries[selected].label, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

/** Temporary foundation screen that proves the database is wired up end to end. */
@Composable
private fun FoundationScreen(app: ATrackerApp) {
    val count by app.database.libraryDao().observeCount().collectAsState(initial = 0)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text("ATracker", style = MaterialTheme.typography.headlineLarge)
        Text("Titles in your library: $count", style = MaterialTheme.typography.bodyLarge)
    }
}
