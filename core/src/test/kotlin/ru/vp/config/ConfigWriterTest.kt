package ru.vp.config

import org.junit.jupiter.api.io.TempDir
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigWriterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writes yaml that loader can read`() {
        val file = tempDir.resolve("vendors.yml")
        val config = Config(
            options = Options(
                baseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
                auth = Auth(method = "basic", username = "admin", password = "secret", token = null),
                sinceDate = "2026-03-04",
                untilDate = "2026-06-04",
                merges = "exclude",
                order = "newest",
                outputDir = "output",
                archive = true,
                debug = false,
                insecure = true,
                timeoutSeconds = 60,
                retries = 0,
                ssh = null,
            ),
            exports = listOf(Group(listOf("A", "B"), listOf("petrov.iv"))),
        )

        Writer().write(file, config)

        assertEquals(config, Loader().load(file))
    }

    @Test
    fun `write failure maps to config write error`() {
        val directory = tempDir.resolve("config.yml")
        Files.createDirectory(directory)

        val error = assertFailsWith<VpException> {
            Writer().write(directory, config())
        }

        assertEquals(ExitCode.CONFIG_WRITE_ERROR, error.exitCode)
    }

    private fun config(): Config =
        Config(
            options = Options(
                baseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
                auth = Auth(method = "basic", username = "admin", password = "secret", token = null),
                sinceDate = "2026-03-04",
                untilDate = "2026-06-04",
                merges = "exclude",
                order = "newest",
                outputDir = "output",
                archive = true,
                debug = false,
                insecure = true,
                timeoutSeconds = 60,
                retries = 0,
                ssh = null,
            ),
            exports = listOf(Group(listOf("A", "B"), listOf("petrov.iv"))),
        )
}
