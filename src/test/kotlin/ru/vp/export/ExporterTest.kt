package ru.vp.export

import org.junit.jupiter.api.io.TempDir
import ru.vp.awesomegraphs.CsvDownloadResult
import ru.vp.awesomegraphs.CsvDownloader
import ru.vp.config.ExportConfig
import ru.vp.config.OptionsConfig
import ru.vp.config.VpConfig
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `exports csv files sequentially into configured output tree and prints progress`() {
        val stdoutBytes = ByteArrayOutputStream()
        val calls = mutableListOf<String>()
        val exporter = Exporter(
            downloaderFactory = {
                CsvDownloader { slug ->
                    calls += slug
                    csvResult(slug, "$slug,csv\n")
                }
            },
            stdout = PrintStream(stdoutBytes),
        )

        val result = exporter.export(config(slugs = listOf("petrov.iv", "ivanov.ia")))

        assertEquals(2, result.filesWritten)
        assertEquals(listOf("petrov.iv", "ivanov.ia"), calls)
        assertEquals("petrov.iv,csv\n", outputFile("petrov.iv").readText())
        assertEquals("ivanov.ia,csv\n", outputFile("ivanov.ia").readText())
        val stdout = stdoutBytes.toString(Charsets.UTF_8)
        assertTrue(stdout.contains("[1/2] exporting petrov.iv -> ${outputFile("petrov.iv")}"))
        assertTrue(stdout.contains("[2/2] exporting ivanov.ia -> ${outputFile("ivanov.ia")}"))
    }

    @Test
    fun `debug mode writes log and summary without token`() {
        val exporter = Exporter(
            downloaderFactory = {
                CsvDownloader { slug ->
                    csvResult(
                        slug = slug,
                        body = "hash\nabc\n",
                        requestUrl = "https://stash.example/rest/awesome-graphs-api/latest/users/$slug/commits/export/csv",
                    )
                }
            },
            stdout = PrintStream(ByteArrayOutputStream()),
        )

        exporter.export(config(debug = true, slugs = listOf("petrov.iv")))

        val debugLog = tempDir.resolve("output").resolve("vp-debug.log").readText()
        val summary = tempDir.resolve("output").resolve("export-summary.csv").readText()
        assertTrue(debugLog.contains("https://stash.example/rest/awesome-graphs-api/latest/users/petrov.iv/commits/export/csv"))
        assertTrue(debugLog.contains("status=200"))
        assertFalse(debugLog.contains("secret-token"))
        assertEquals(
            "path,slug,file,bytes\n" +
                "\"Аутсорсинг/ООО Ромашка\",petrov.iv,\"${outputFile("petrov.iv")}\",9\n",
            summary,
        )
    }

    @Test
    fun `export fails fast and does not continue after first failed slug`() {
        val calls = mutableListOf<String>()
        val exporter = Exporter(
            downloaderFactory = {
                CsvDownloader { slug ->
                    calls += slug
                    if (slug == "ivanov.ia") {
                        throw VpException(ExitCode.USER_NOT_FOUND, "missing user")
                    }
                    csvResult(slug, "$slug,csv\n")
                }
            },
            stdout = PrintStream(ByteArrayOutputStream()),
        )

        val error = assertFailsWith<VpException> {
            exporter.export(config(slugs = listOf("petrov.iv", "ivanov.ia", "sidorov.sp")))
        }

        assertEquals(ExitCode.USER_NOT_FOUND, error.exitCode)
        assertEquals(listOf("petrov.iv", "ivanov.ia"), calls)
        assertTrue(Files.exists(outputFile("petrov.iv")))
        assertFalse(Files.exists(outputFile("ivanov.ia")))
        assertFalse(Files.exists(outputFile("sidorov.sp")))
    }

    @Test
    fun `archive mode creates zip after successful export and records it in debug log`() {
        val exporter = Exporter(
            downloaderFactory = {
                CsvDownloader { slug -> csvResult(slug, "$slug,csv\n") }
            },
            stdout = PrintStream(ByteArrayOutputStream()),
        )

        val result = exporter.export(config(debug = true, archive = true, slugs = listOf("petrov.iv")))

        val archivePath = tempDir.resolve("output.zip")
        assertEquals(archivePath, result.archivePath)
        ZipFile(archivePath.toFile()).use { zip ->
            val entry = zip.getEntry("Аутсорсинг/ООО Ромашка/petrov.iv-2026-03-04_2026-06-04-commits.csv")
            assertEquals("petrov.iv,csv\n", zip.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8))
        }
        val debugLog = tempDir.resolve("output").resolve("vp-debug.log").readText()
        assertTrue(debugLog.contains("archiveCreated=$archivePath entries=3"))
    }

    private fun config(
        debug: Boolean = false,
        archive: Boolean = false,
        slugs: List<String> = listOf("petrov.iv"),
    ): VpConfig = VpConfig(
        options = OptionsConfig(
            baseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            token = "secret-token",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = tempDir.resolve("output").toString(),
            archive = archive,
            debug = debug,
            timeoutSeconds = 60,
            retries = 0,
        ),
        exports = listOf(
            ExportConfig(
                path = listOf("Аутсорсинг", "ООО Ромашка"),
                slugs = slugs,
            ),
        ),
    )

    private fun outputFile(slug: String): Path = tempDir.resolve("output")
        .resolve("Аутсорсинг")
        .resolve("ООО Ромашка")
        .resolve("$slug-2026-03-04_2026-06-04-commits.csv")

    private fun csvResult(
        slug: String,
        body: String,
        requestUrl: String = "https://stash.example/users/$slug/commits/export/csv",
    ): CsvDownloadResult = CsvDownloadResult(
        bytes = body.toByteArray(Charsets.UTF_8),
        requestUrl = requestUrl,
        statusCode = 200,
        contentType = "text/csv",
        attempts = 1,
    )
}
