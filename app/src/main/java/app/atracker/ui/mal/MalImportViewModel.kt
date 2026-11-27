package app.atracker.ui.mal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atracker.ATrackerApp
import app.atracker.data.db.AppSettingEntity
import app.atracker.data.db.ImportRecordEntity
import app.atracker.data.db.ImportRunEntity
import app.atracker.data.mal.ConflictPolicy
import app.atracker.data.mal.ImportPlan
import app.atracker.data.mal.ImportReport
import app.atracker.data.mal.MalApi
import app.atracker.data.mal.MalAuth
import app.atracker.data.mal.MalImporter
import app.atracker.data.mal.MalKind
import app.atracker.data.mal.MalRecord
import app.atracker.data.mal.MalXml
import app.atracker.data.mal.PlanAction
import app.atracker.data.provider.AniListProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ImportUi {
    data object Idle : ImportUi
    data class Working(val message: String) : ImportUi
    data class Preview(val plan: ImportPlan, val source: String, val policy: ConflictPolicy, val note: String?) : ImportUi
    data class Done(val report: ImportReport) : ImportUi
    data class Error(val message: String) : ImportUi
}

const val KEY_MAL_CLIENT_ID = "mal_client_id"
private const val KEY_MAL_PKCE = "mal_pkce"

class MalImportViewModel(private val app: ATrackerApp) : ViewModel() {
    private val settings = app.database.settingDao()
    private val importer = MalImporter(
        app.database, app.mediaRepository,
        app.catalogueRepository.providers.filterIsInstance<AniListProvider>().firstOrNull()
    )

    val clientId: StateFlow<String?> = settings.observe(KEY_MAL_CLIENT_ID).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val runs: StateFlow<List<ImportRunEntity>> = app.database.importDao().observeRuns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _connected = MutableStateFlow(app.tokenStore.load() != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _ui = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val ui: StateFlow<ImportUi> = _ui.asStateFlow()
    private val _records = MutableStateFlow<Pair<ImportRunEntity, List<ImportRecordEntity>>?>(null)
    val records: StateFlow<Pair<ImportRunEntity, List<ImportRecordEntity>>?> = _records.asStateFlow()

    init { viewModelScope.launch { app.database.importDao().failInterrupted(System.currentTimeMillis()) } }

    fun saveClientId(v: String) { viewModelScope.launch { settings.put(AppSettingEntity(KEY_MAL_CLIENT_ID, v.trim())) } }

    /** Opens MyAnimeList's sign-in page in the browser. The result returns through the atracker://mal-auth link. */
    fun connect(context: Context) {
        val id = clientId.value?.trim().orEmpty()
        if (id.isEmpty()) { _ui.value = ImportUi.Error("Enter your MyAnimeList Client ID first."); return }
        val verifier = MalAuth.newVerifier()
        val state = UUID.randomUUID().toString()
        viewModelScope.launch {
            settings.put(AppSettingEntity(KEY_MAL_PKCE, "$state|$verifier"))
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MalAuth.authorizeUrl(id, verifier, state))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            val error = uri.getQueryParameter("error")
            if (error != null) { _ui.value = ImportUi.Error("MyAnimeList sign-in was cancelled or refused ($error)."); return@launch }
            val code = uri.getQueryParameter("code") ?: return@launch
            val saved = settings.get(KEY_MAL_PKCE)?.split('|')
            if (saved == null || saved.size != 2 || saved[0] != uri.getQueryParameter("state")) {
                _ui.value = ImportUi.Error("The sign-in response did not match this request. Try connecting again."); return@launch
            }
            _ui.value = ImportUi.Working("Finishing sign-in…")
            try {
                val token = MalAuth.exchange(clientId.value.orEmpty().trim(), code, saved[1])
                app.tokenStore.save(token); _connected.value = true
                settings.put(AppSettingEntity(KEY_MAL_PKCE, ""))
                _ui.value = ImportUi.Idle
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _ui.value = ImportUi.Error(e.message ?: "Sign-in failed.")
            }
        }
    }

    fun disconnect() { app.tokenStore.clear(); _connected.value = false }

    private suspend fun validToken(): String {
        val t = app.tokenStore.load() ?: throw MalApi.Unauthorized()
        if (t.expiresAtMs - 60_000 > System.currentTimeMillis()) return t.access
        if (t.refresh.isEmpty()) throw MalApi.Unauthorized()
        val fresh = MalAuth.refresh(clientId.value.orEmpty().trim(), t.refresh)
        app.tokenStore.save(fresh.copy(refresh = fresh.refresh.ifEmpty { t.refresh }))
        return fresh.access
    }

    fun fetchFromMal() = run("MAL_API") { say ->
        val api = MalApi(validToken())
        val all = ArrayList<MalRecord>()
        for (kind in MalKind.entries) {
            say("Reading your ${kind.key} list…")
            all += api.fetchList(kind) { n -> say("Reading your ${kind.key} list… $n so far") }
        }
        Pair(all, if (api.usedExtendedFields) null else "MyAnimeList did not provide dates, tags or comments through the API for this account, so only status, progress and score were read. The export-file import carries those.")
    }

    fun importFile(context: Context, uri: Uri) = run("MAL_XML") { say ->
        say("Reading the export file…")
        val stream = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open that file.")
        val name = uri.lastPathSegment.orEmpty()
        val records = stream.use { s ->
            if (name.endsWith(".gz")) java.util.zip.GZIPInputStream(s).use { MalXml.parse(it) } else MalXml.parse(s)
        }
        Pair(records, null)
    }

    private fun run(source: String, load: suspend (say: (String) -> Unit) -> Pair<List<MalRecord>, String?>) {
        viewModelScope.launch {
            _ui.value = ImportUi.Working("Starting…")
            try {
                val (records, note) = load { _ui.value = ImportUi.Working(it) }
                if (records.isEmpty()) { _ui.value = ImportUi.Error("No anime or manga entries were found."); return@launch }
                val plan = importer.plan(records) { _ui.value = ImportUi.Working(it) }
                _ui.value = ImportUi.Preview(plan.copy(note = listOfNotNull(plan.note, note).joinToString(" ").ifBlank { null }), source, ConflictPolicy.KEEP_MINE, null)
            } catch (e: CancellationException) { throw e } catch (e: MalApi.Unauthorized) {
                disconnect(); _ui.value = ImportUi.Error(e.message ?: "Sign-in expired.")
            } catch (e: Exception) {
                _ui.value = ImportUi.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun mutate(f: (ImportUi.Preview) -> ImportUi.Preview) { (_ui.value as? ImportUi.Preview)?.let { _ui.value = f(it) } }
    fun setPolicy(p: ConflictPolicy) = mutate { it.copy(policy = p) }
    fun setSkip(index: Int, skip: Boolean) = mutate { p ->
        p.copy(plan = p.plan.copy(items = p.plan.items.mapIndexed { i, it -> if (i == index) it.copy(skip = skip) else it }))
    }
    /** For possible duplicates: true attaches the MAL entry to the title already in the app, false adds it separately. */
    fun setLink(index: Int, link: Boolean) = mutate { p ->
        p.copy(plan = p.plan.copy(items = p.plan.items.mapIndexed { i, it ->
            if (i == index && it.action == PlanAction.POSSIBLE_DUPLICATE) it.copy(linkToMediaId = if (link) it.existingMediaId else null) else it
        }))
    }
    fun skipAll(action: PlanAction, skip: Boolean) = mutate { p ->
        p.copy(plan = p.plan.copy(items = p.plan.items.map { if (it.action == action) it.copy(skip = skip) else it }))
    }

    fun confirm() {
        val p = _ui.value as? ImportUi.Preview ?: return
        viewModelScope.launch {
            _ui.value = ImportUi.Working("Importing ${p.plan.items.count { !it.skip }} titles into your library…")
            try { _ui.value = ImportUi.Done(importer.commit(p.plan, p.source, p.policy)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.value = ImportUi.Error("Import stopped: ${e.message}. Titles already imported are kept; run it again to finish. Nothing is duplicated.") }
        }
    }

    fun reset() { _ui.value = ImportUi.Idle }
    fun openRun(run: ImportRunEntity) { viewModelScope.launch { _records.value = run to app.database.importDao().recordsOnce(run.id) } }
    fun closeRun() { _records.value = null }
}
