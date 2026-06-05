package ru.vp.config

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
