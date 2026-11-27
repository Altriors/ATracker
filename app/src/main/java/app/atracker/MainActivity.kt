package app.atracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.atracker.ui.calendar.CalendarScreen
import app.atracker.ui.detail.DetailScreen
import app.atracker.ui.franchise.FranchiseListScreen
import app.atracker.ui.franchise.FranchiseScreen
import app.atracker.ui.discover.DiscoverScreen
import app.atracker.ui.home.HomeScreen
import app.atracker.ui.library.LibraryScreen
import app.atracker.ui.manual.ManualEntryScreen
import app.atracker.ui.mal.MalImportScreen
import app.atracker.ui.settings.KEY_THEME
import app.atracker.ui.settings.SettingsScreen
import app.atracker.ui.stats.StatsScreen
import app.atracker.ui.theme.ATrackerTheme
import java.net.URLDecoder
import java.net.URLEncoder

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    DISCOVER("discover", "Discover", Icons.Filled.Explore),
    LIBRARY("library", "My Library", Icons.Filled.LibraryBooks),
    STATS("stats", "Statistics", Icons.Filled.InsertChart),
    SETTINGS("settings", "Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { if (it.scheme == "atracker") (application as ATrackerApp).pendingMalRedirect.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ATrackerApp
        intent?.data?.let { if (it.scheme == "atracker") app.pendingMalRedirect.value = it }
        setContent {
            val pref by app.database.settingDao().observe(KEY_THEME).collectAsState(initial = null)
            val dark = when (pref) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            ATrackerTheme(darkTheme = dark) { AppRoot(app) }
        }
    }
}

@Composable
private fun AppRoot(app: ATrackerApp) {
    val context = LocalContext.current
    fun restartApp() = context.restartApp()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val redirect by app.pendingMalRedirect.collectAsState()
    androidx.compose.runtime.LaunchedEffect(redirect) { if (redirect != null && route != "mal") nav.navigate("mal") }

    fun goTab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun openDetail(id: Long) = nav.navigate("detail/$id")
    fun openManual(title: String) = nav.navigate("manual?title=${URLEncoder.encode(title, "UTF-8")}&id=-1")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { goTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(if (showBar) padding else PaddingValues(0.dp)).fillMaxSize()) {
            composable(Tab.HOME.route) { HomeScreen(app, ::openDetail) { goTab(Tab.DISCOVER) } }
            composable(Tab.DISCOVER.route) { DiscoverScreen(app, ::openDetail, ::openManual) }
            composable(Tab.LIBRARY.route) { LibraryScreen(app, ::openDetail, onDiscover = { goTab(Tab.DISCOVER) }, onFranchises = { nav.navigate("franchises") }, onCalendar = { nav.navigate("calendar") }) }
            composable(Tab.STATS.route) { StatsScreen(app, ::openDetail) }
            composable(Tab.SETTINGS.route) { SettingsScreen(app, onRestart = ::restartApp, onOpenMal = { nav.navigate("mal") }) }
            composable("mal") { MalImportScreen(app, onBack = { nav.popBackStack() }) }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                DetailScreen(
                    app, id, onBack = { nav.popBackStack() }, onEditManual = { nav.navigate("manual?title=&id=$it") },
                    onOpenTitle = ::openDetail, onOpenFranchise = { nav.navigate("franchise/$it") }
                )
            }
            composable("calendar") { CalendarScreen(app, onBack = { nav.popBackStack() }, onOpen = ::openDetail) }
            composable("franchises") {
                FranchiseListScreen(app, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("franchise/$it") })
            }
            composable("franchise/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                FranchiseScreen(app, id, onBack = { nav.popBackStack() }, onOpenTitle = ::openDetail)
            }
            composable(
                "manual?title={title}&id={id}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val title = URLDecoder.decode(entry.arguments?.getString("title").orEmpty(), "UTF-8")
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                ManualEntryScreen(
                    app, id, title, onBack = { nav.popBackStack() },
                    onSaved = { saved ->
                        nav.popBackStack()
                        if (id == null) nav.navigate("detail/$saved")
                    }
                )
            }
        }
    }
}

/** Relaunches the app process so a restored database is opened fresh. */
private fun Context.restartApp() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    startActivity(intent)
    Runtime.getRuntime().exit(0)
}
