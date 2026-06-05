package ru.vp.paths

import org.junit.jupiter.api.io.TempDir
import ru.vp.config.Config
import ru.vp.config.Group
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayoutTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `plans unicode output tree and csv file names without rewriting path segments`() {
        val outputDir = tempDir.resolve("output")

        val plan = Layout().plan(config(outputDir = outputDir))

        assertEquals(outputDir, plan.dir)
        assertEquals(outputDir.resolve("Аутсорсинг").resolve("ООО Ромашка"), plan.files.single().dir)
        assertEquals(
            outputDir
                .resolve("Аутсорсинг")
                .resolve("ООО Ромашка")
                .resolve("petrov.iv-2026-03-04_2026-06-04-commits.csv"),
            plan.files.single().file,
        )
    }

    @Test
    fun `existing output directory fails before planning writes`() {
        val outputDir = tempDir.resolve("output")
        Files.createDirectory(outputDir)

        val error = assertFailsWith<VpException> {
            Layout().plan(config(outputDir = outputDir))
        }

        assertEquals(ExitCode.OUTPUT_DIR_EXISTS, error.exitCode)
    }

    @Test
    fun `existing archive fails before planning writes when archive is enabled`() {
        val outputDir = tempDir.resolve("output")
        Files.writeString(tempDir.resolve("output.zip"), "already exists")

        val error = assertFailsWith<VpException> {
            Layout().plan(config(outputDir = outputDir, archive = true))
        }

        assertEquals(ExitCode.ARCHIVE_EXISTS, error.exitCode)
    }

    @Test
    fun `invalid export path segment fails`() {
        val error = assertFailsWith<VpException> {
            Layout().plan(config(path = listOf("Vendor", "bad/name")))
        }

        assertEquals(ExitCode.INVALID_OUTPUT_PATH, error.exitCode)
    }

    private fun config(
        outputDir: Path = tempDir.resolve("output"),
        archive: Boolean = false,
        path: List<String> = listOf("Аутсорсинг", "ООО Ромашка"),
    ): Config = Config(
        options = Options(
            baseUrl = "https://stash.nspk.ru/rest/awesome-graphs-api/latest",
            token = "secret",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = outputDir.toString(),
            archive = archive,
            debug = false,
            timeoutSeconds = 60,
            retries = 0,
        ),
        exports = listOf(
            Group(
                path = path,
                slugs = listOf("petrov.iv"),
            ),
        ),
    )
}
