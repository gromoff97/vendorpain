package ru.vp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.vp.bitbucket.BitbucketUser
import ru.vp.config.Config
import ru.vp.config.Rules
import ru.vp.error.VpException
import ru.vp.export.ExportResult
import ru.vp.export.ExportProgress
import ru.vp.export.Exporter
import java.awt.EventQueue
import java.nio.file.Files
import java.nio.file.Path

class AppState(
    private val usersFactory: (ExportForm) -> UserSearch = { form -> BitbucketUserSearch(form) },
    private val configFiles: ConfigFileStore = ConfigFiles(),
    private val preferences: PreferencesStore = UiPreferences(),
    private val exporter: (Config, (ExportProgress) -> Unit) -> ExportResult = { config, progress ->
        Exporter().export(config, progress)
    },
) {
    var form by mutableStateOf(ExportForm())
    var query by mutableStateOf("")
    var suggestions by mutableStateOf(emptyList<BitbucketUser>())
    var running by mutableStateOf(false)
    var progress by mutableStateOf(ExportProgress(0, 0, null, null))
    var message by mutableStateOf("")
    var logs by mutableStateOf(listOf("vendorpain UI started"))
    var activeGroupIndex by mutableStateOf(0)
    var currentConfigPath by mutableStateOf(preferences.lastConfigPath())

    init {
        currentConfigPath
            ?.takeIf { Files.isRegularFile(it) && Files.isReadable(it) }
            ?.let { path -> runCatching { form = configFiles.load(path) }.onFailure(::fail) }
    }

    fun load(path: Path) {
        form = configFiles.load(path)
        currentConfigPath = path
        preferences.saveLastConfigPath(path)
        message = "Loaded config: $path"
        log("loaded config: $path")
    }

    fun save(path: Path? = currentConfigPath) {
        require(path != null) { "config path is not selected" }
        saveAs(path)
    }

    fun saveAs(path: Path) {
        configFiles.save(path, form)
        currentConfigPath = path
        preferences.saveLastConfigPath(path)
        message = "Saved config: $path"
        log("saved config: $path")
    }

    fun select(user: BitbucketUser) {
        val groups = form.groups.ifEmpty { listOf(UiExportGroup()) }
        val index = activeGroupIndex.coerceIn(groups.indices)
        val group = groups[index]
        val updated = if (group.users.any { it.slug == user.slug }) {
            group
        } else {
            group.copy(users = group.users + user)
        }

        form = form.copy(groups = groups.toMutableList().also { it[index] = updated })
        query = ""
        suggestions = emptyList()
    }

    fun remove(user: BitbucketUser) {
        form = form.copy(
            users = form.users.filterNot { it.slug == user.slug },
            groups = form.groups.map { group ->
                group.copy(users = group.users.filterNot { it.slug == user.slug })
            },
        )
    }

    fun updateGroup(index: Int, group: UiExportGroup) {
        if (index !in form.groups.indices) return
        form = form.copy(groups = form.groups.toMutableList().also { it[index] = group })
    }

    fun removeGroup(index: Int) {
        if (index !in form.groups.indices || form.groups.size == 1) return
        form = form.copy(groups = form.groups.filterIndexed { i, _ -> i != index })
        activeGroupIndex = activeGroupIndex.coerceAtMost(form.groups.lastIndex)
    }

    fun removeFromGroup(groupIndex: Int, user: BitbucketUser) {
        if (groupIndex !in form.groups.indices) return
        val group = form.groups[groupIndex]
        updateGroup(groupIndex, group.copy(users = group.users.filterNot { it.slug == user.slug }))
    }

    fun search(): List<BitbucketUser> {
        if (query.length < 2 || form.bitbucketBaseUrl.isBlank() || !form.hasAuthInput()) {
            return emptyList()
        }

        val users = usersFactory(form)
        val term = query
        return try {
            users.search(term).also { log("search $term -> ${it.size} users") }
        } finally {
            users.close()
        }
    }

    fun show(users: List<BitbucketUser>) {
        suggestions = users
    }

    fun start() =
        Rules().validate(form.config()).also { config ->
            running = true
            message = ""
            progress = ExportProgress(0, config.exports.sumOf { it.slugs.size }, null, null)
            log("export started: ${progress.total} users")
        }

    fun export(config: Config): ExportResult =
        exporter(config) { progress ->
            EventQueue.invokeAndWait {
                this.progress = progress
                progress.slug?.let { slug -> log("exporting ${progress.done + 1}/${progress.total}: $slug") }
            }
        }

    fun done(result: ExportResult) {
        message = "Export completed: ${result.files} CSV files written to ${result.dir}" +
            result.zip?.let { "\nArchive created: $it" }.orEmpty()
        running = false
        log("export completed: ${result.files} files -> ${result.dir}")
    }

    fun fail(error: Throwable) {
        running = false
        message = error.message ?: "Unexpected error"
        val code = (error as? VpException)?.exitCode?.code
        log(if (code == null) "error: $message" else "error $code: $message")
    }

    private fun log(line: String) {
        logs = (logs + line).takeLast(300)
    }
}
