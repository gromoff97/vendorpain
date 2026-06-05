package ru.vp.cli

import org.junit.jupiter.api.io.TempDir
import ru.vp.config.Config
import ru.vp.config.Group
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.export.ExportResult
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing conf option returns invalid cli args`() {
        val streams = Streams()

        val code = VpCli().execute(emptyArray(), stdout = streams.stdout, stderr = streams.stderr)

        assertEquals(ExitCode.INVALID_CLI_ARGS.code, code)
        assertTrue(streams.errText().contains("--conf"))
    }

    @Test
    fun `unreadable config maps to config not readable`() {
        val streams = Streams()

        val code = VpCli().execute(
            arrayOf("--conf", tempDir.resolve("missing.yml").toString()),
            stdout = streams.stdout,
            stderr = streams.stderr,
        )

        assertEquals(ExitCode.CONFIG_NOT_READABLE.code, code)
        assertTrue(streams.errText().contains("config file is not readable"))
    }

    @Test
    fun `success flow loads config runs exporter and prints summary`() {
        val streams = Streams()
        var loadedPath: Path? = null
        var exportedConfig: Config? = null
        val configPath = tempDir.resolve("vendors.yml")
        val config = validConfig()

        val code = VpCli(
            configLoader = { path ->
                loadedPath = path
                config
            },
            exportAction = { loadedConfig, _ ->
                exportedConfig = loadedConfig
                ExportResult(
                    dir = tempDir.resolve("output"),
                    zip = tempDir.resolve("output.zip"),
                    files = 2,
                )
            },
        ).execute(arrayOf("--conf", configPath.toString()), stdout = streams.stdout, stderr = streams.stderr)

        assertEquals(ExitCode.SUCCESS.code, code)
        assertEquals(configPath, loadedPath)
        assertEquals(config, exportedConfig)
        assertTrue(streams.outText().contains("Export completed: 2 CSV files written to ${tempDir.resolve("output")}"))
        assertTrue(streams.outText().contains("Archive created: ${tempDir.resolve("output.zip")}"))
        assertEquals("", streams.errText())
    }

    @Test
    fun `domain exception is mapped to its exit code and token is not printed`() {
        val streams = Streams()

        val code = VpCli(
            configLoader = { validConfig() },
            exportAction = { _, _ ->
                throw VpException(ExitCode.PERMISSION_DENIED, "permission denied while downloading CSV")
            },
        ).execute(arrayOf("--conf", "vendors.yml"), stdout = streams.stdout, stderr = streams.stderr)

        assertEquals(ExitCode.PERMISSION_DENIED.code, code)
        assertTrue(streams.errText().contains("permission denied"))
        assertFalse(streams.errText().contains("secret-token"))
    }

    private fun validConfig(): Config = Config(
        options = Options(
            baseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            token = "secret-token",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = tempDir.resolve("output").toString(),
            archive = false,
            debug = false,
            timeoutSeconds = 60,
            retries = 0,
        ),
        exports = listOf(Group(path = listOf("Vendor"), slugs = listOf("petrov.iv"))),
    )

    private class Streams {
        private val stdoutBytes = ByteArrayOutputStream()
        private val stderrBytes = ByteArrayOutputStream()
        val stdout = PrintStream(stdoutBytes)
        val stderr = PrintStream(stderrBytes)

        fun outText(): String = stdoutBytes.toString(Charsets.UTF_8)
        fun errText(): String = stderrBytes.toString(Charsets.UTF_8)
    }
}
