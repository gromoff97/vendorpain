package ru.vp.cli

import org.junit.jupiter.api.io.TempDir
import ru.vp.config.Auth
import ru.vp.config.Config
import ru.vp.config.Group
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.export.ExportProgress
import ru.vp.export.ExportResult
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.writeText
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
            exportAction = { loadedConfig, progress ->
                exportedConfig = loadedConfig
                progress(ExportProgress(0, 2, "petrov.iv", tempDir.resolve("output/petrov.iv.csv")))
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
        assertTrue(streams.outText().contains("[1/2] exporting petrov.iv -> ${tempDir.resolve("output/petrov.iv.csv")}"))
        assertTrue(streams.outText().contains("Export completed: 2 CSV files written to ${tempDir.resolve("output")}"))
        assertTrue(streams.outText().contains("Archive created: ${tempDir.resolve("output.zip")}"))
        assertEquals("", streams.errText())
    }

    @Test
    fun `domain exception is mapped to its exit code and password is not printed`() {
        val streams = Streams()

        val code = VpCli(
            configLoader = { validConfig() },
            exportAction = { _, _ ->
                throw VpException(ExitCode.PERMISSION_DENIED, "permission denied while downloading CSV")
            },
        ).execute(arrayOf("--conf", "vendors.yml"), stdout = streams.stdout, stderr = streams.stderr)

        assertEquals(ExitCode.PERMISSION_DENIED.code, code)
        assertTrue(streams.errText().contains("permission denied"))
        assertFalse(streams.errText().contains("secret-password"))
    }

    @Test
    fun `unexpected exception is mapped to internal error`() {
        val streams = Streams()

        val code = VpCli(
            configLoader = { validConfig() },
            exportAction = { _, _ -> error("boom") },
        ).execute(arrayOf("--conf", "vendors.yml"), stdout = streams.stdout, stderr = streams.stderr)

        assertEquals(ExitCode.INTERNAL_ERROR.code, code)
        assertTrue(streams.errText().contains("unexpected internal error"))
        assertFalse(streams.errText().contains("boom"))
    }

    @Test
    fun `missing insecure option returns invalid config schema`() {
        val streams = Streams()
        val configPath = tempDir.resolve("vendors.yml")
        configPath.writeText(
            """
options:
  baseUrl: "https://stash.example/rest/awesome-graphs-api/latest"
  auth:
    method: "basic"
    username: "petrov.iv"
    password: "secret-password"
    token: null
  sinceDate: "2026-03-04"
  untilDate: "2026-06-04"
  merges: "exclude"
  order: "newest"
  outputDir: "${tempDir.resolve("output")}"
  archive: false
  debug: false
  timeoutSeconds: 60
  retries: 0
  ssh: null
exports:
  - path: ["Vendor"]
    slugs:
      - petrov.iv
""".trimStart(),
        )

        val code = VpCli(
            exportAction = { _, _ -> error("export should not run for invalid config") },
        ).execute(arrayOf("--conf", configPath.toString()), stdout = streams.stdout, stderr = streams.stderr)

        assertEquals(ExitCode.INVALID_CONFIG_SCHEMA.code, code)
        assertTrue(streams.errText().contains("invalid config schema"))
    }

    private fun validConfig(): Config = Config(
        options = Options(
            baseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            auth = Auth(method = "basic", username = "petrov.iv", password = "secret-password"),
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = tempDir.resolve("output").toString(),
            archive = false,
            debug = false,
            insecure = false,
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
