package ru.vp.ui

import org.junit.jupiter.api.io.TempDir
import ru.vp.bitbucket.BitbucketUser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `load config updates form and remembered path`() {
        val file = tempDir.resolve("vp.yml")
        val files = FakeConfigFiles(
            loaded = ExportForm(
                bitbucketBaseUrl = "https://stash.example",
                graphsBaseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
                authMethod = "basic",
                username = "admin",
                password = "secret",
                sinceDate = "2026-03-04",
                untilDate = "2026-06-04",
                outputDir = "output",
                insecure = true,
                groups = listOf(UiExportGroup(name = "Vendor", pathText = "Vendor")),
            ),
        )
        val prefs = FakeUiPreferences()
        val state = AppState(configFiles = files, preferences = prefs)

        state.load(file)

        assertEquals("https://stash.example", state.form.bitbucketBaseUrl)
        assertEquals(true, state.form.insecure)
        assertEquals(file, prefs.saved)
    }

    @Test
    fun `save config writes current form and remembered path`() {
        val file = tempDir.resolve("vp.yml")
        val files = FakeConfigFiles()
        val prefs = FakeUiPreferences()
        val state = AppState(configFiles = files, preferences = prefs)
        state.form = state.form.copy(
            graphsBaseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            username = "admin",
            password = "secret",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            outputDir = "output",
            groups = listOf(
                UiExportGroup(
                    pathText = "Vendor",
                    users = listOf(BitbucketUser("petrov.iv", "petrov.iv", "Петров", null, true)),
                ),
            ),
        )

        state.saveAs(file)

        assertEquals(file, files.savedPath)
        assertEquals(file, prefs.saved)
    }

    @Test
    fun `search passes insecure and ssh settings to user client`() {
        val captured = mutableListOf<ExportForm>()
        val state = AppState(
            usersFactory = { form ->
                captured += form
                FakeUserSearch(listOf(BitbucketUser("petrov.iv", "petrov.iv", "Петров", null, true)))
            },
        )
        state.form = state.form.copy(
            bitbucketBaseUrl = "https://stash.example",
            authMethod = "basic",
            username = "admin",
            password = "secret",
            insecure = true,
            sshEnabled = true,
            sshHost = "vdi-wsl",
            sshUser = "anton",
            sshPassword = "secret",
        )
        state.query = "pet"

        val results = state.search()

        assertEquals(listOf("petrov.iv"), results.map { it.slug })
        assertEquals(true, captured.single().insecure)
        assertEquals(true, captured.single().sshEnabled)
    }

    private class FakeUserSearch(
        private val users: List<BitbucketUser>,
    ) : UserSearch {
        override fun search(query: String): List<BitbucketUser> = users
        override fun close() = Unit
    }

    private class FakeConfigFiles(
        private val loaded: ExportForm = ExportForm(),
    ) : ConfigFileStore {
        var savedPath: Path? = null

        override fun save(path: Path, form: ExportForm) {
            savedPath = path
        }

        override fun load(path: Path): ExportForm = loaded
    }

    private class FakeUiPreferences : PreferencesStore {
        var saved: Path? = null

        override fun lastConfigPath(): Path? = saved

        override fun saveLastConfigPath(path: Path) {
            saved = path
        }
    }
}
