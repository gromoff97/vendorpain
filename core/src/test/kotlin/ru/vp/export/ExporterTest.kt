package ru.vp.export

import org.junit.jupiter.api.io.TempDir
import ru.vp.awesomegraphs.CsvResult
import ru.vp.awesomegraphs.CsvSource
import ru.vp.config.Auth
import ru.vp.config.Config
import ru.vp.config.Group
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
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
    fun `exports csv files sequentially into configured output tree and reports progress`() {
        val calls = mutableListOf<String>()
        val progress = mutableListOf<ExportProgress>()
        val exporter = Exporter(
            clients = {
                CsvSource { slug ->
                    calls += slug
                    csvResult(slug, "$slug,csv\n")
                }
            },
        )

        val result = exporter.export(config(slugs = listOf("petrov.iv", "ivanov.ia")), progress::add)

        assertEquals(2, result.files)
        assertEquals(listOf("petrov.iv", "ivanov.ia"), calls)
        assertEquals("petrov.iv,csv\n", outputFile("petrov.iv").readText())
        assertEquals("ivanov.ia,csv\n", outputFile("ivanov.ia").readText())
        assertEquals(
            listOf(
                ExportProgress(done = 0, total = 2, slug = "petrov.iv", file = outputFile("petrov.iv")),
                ExportProgress(done = 1, total = 2, slug = "ivanov.ia", file = outputFile("ivanov.ia")),
                ExportProgress(done = 2, total = 2, slug = null, file = null),
            ),
            progress,
        )
    }

    @Test
    fun `debug mode writes log and summary without password`() {
        val exporter = Exporter(
            clients = {
                CsvSource { slug ->
                    csvResult(
                        slug = slug,
                        body = "hash\nabc\n",
                        url = "https://stash.example/rest/awesome-graphs-api/latest/users/$slug/commits/export/csv",
                    )
                }
            },
        )

        exporter.export(config(debug = true, slugs = listOf("petrov.iv")))

        val debugLog = tempDir.resolve("output").resolve("vp-debug.log").readText()
        val summary = tempDir.resolve("output").resolve("export-summary.csv").readText()
        assertTrue(debugLog.contains("https://stash.example/rest/awesome-graphs-api/latest/users/petrov.iv/commits/export/csv"))
        assertTrue(debugLog.contains("status=200"))
        assertFalse(debugLog.contains("secret-password"))
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
            clients = {
                CsvSource { slug ->
                    calls += slug
                    if (slug == "ivanov.ia") {
                        throw VpException(ExitCode.USER_NOT_FOUND, "missing user")
                    }
                    csvResult(slug, "$slug,csv\n")
                }
            },
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
            clients = {
                CsvSource { slug -> csvResult(slug, "$slug,csv\n") }
            },
        )

        val result = exporter.export(config(debug = true, archive = true, slugs = listOf("petrov.iv")))

        val archivePath = tempDir.resolve("output.zip")
        assertEquals(archivePath, result.zip)
        ZipFile(archivePath.toFile()).use { zip ->
            val entry = zip.getEntry("Аутсорсинг/ООО Ромашка/petrov.iv-2026-03-04_2026-06-04-commits.csv")
            assertEquals("petrov.iv,csv\n", zip.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8))
        }
        val debugLog = tempDir.resolve("output").resolve("vp-debug.log").readText()
        assertTrue(debugLog.contains("archiveCreated=$archivePath entries=3"))
    }

    @Test
    fun `archive mode creates zip when debug is disabled`() {
        val exporter = Exporter(
            clients = {
                CsvSource { slug -> csvResult(slug, "$slug,csv\n") }
            },
        )

        val result = exporter.export(config(debug = false, archive = true, slugs = listOf("petrov.iv")))

        val archivePath = tempDir.resolve("output.zip")
        assertEquals(archivePath, result.zip)
        ZipFile(archivePath.toFile()).use { zip ->
            val entry = zip.getEntry("Аутсорсинг/ООО Ромашка/petrov.iv-2026-03-04_2026-06-04-commits.csv")
            assertEquals("petrov.iv,csv\n", zip.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8))
        }
        assertFalse(Files.exists(tempDir.resolve("output").resolve("vp-debug.log")))
    }

    @Test
    fun `filesystem write failure maps to filesystem error`() {
        val parentFile = tempDir.resolve("not-a-directory")
        Files.writeString(parentFile, "file")
        val exporter = Exporter(
            clients = {
                CsvSource { slug -> csvResult(slug, "$slug,csv\n") }
            },
        )

        val error = assertFailsWith<VpException> {
            exporter.export(config(outputDir = parentFile.resolve("output")))
        }

        assertEquals(ExitCode.FILESYSTEM_ERROR, error.exitCode)
    }

    @Test
    fun `closes csv source after export`() {
        val source = CloseAwareSource()
        val exporter = Exporter(
            clients = { source },
        )

        exporter.export(config(slugs = listOf("petrov.iv")))

        assertTrue(source.closed)
    }

    private class CloseAwareSource : CsvSource {
        var closed = false

        override fun downloadCsv(slug: String): CsvResult =
            CsvResult(
                bytes = "$slug,csv\n".toByteArray(Charsets.UTF_8),
                url = "https://stash.example/users/$slug/commits/export/csv",
                status = 200,
                type = "text/csv",
                tries = 1,
            )

        override fun close() {
            closed = true
        }
    }

    private fun config(
        debug: Boolean = false,
        archive: Boolean = false,
        slugs: List<String> = listOf("petrov.iv"),
        outputDir: Path = tempDir.resolve("output"),
    ): Config = Config(
        options = Options(
            baseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            auth = Auth(method = "basic", username = "petrov.iv", password = "secret-password"),
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = outputDir.toString(),
            archive = archive,
            debug = debug,
            insecure = false,
            timeoutSeconds = 60,
            retries = 0,
        ),
        exports = listOf(
            Group(
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
        url: String = "https://stash.example/users/$slug/commits/export/csv",
    ): CsvResult = CsvResult(
        bytes = body.toByteArray(Charsets.UTF_8),
        url = url,
        status = 200,
        type = "text/csv",
        tries = 1,
    )
}
