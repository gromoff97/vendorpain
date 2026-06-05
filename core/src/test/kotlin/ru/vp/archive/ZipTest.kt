package ru.vp.archive

import org.junit.jupiter.api.io.TempDir
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates zip archive next to output directory with relative file entries`() {
        val outputDir = tempDir.resolve("output")
        val nestedDir = outputDir.resolve("Аутсорсинг").resolve("ООО Ромашка")
        Files.createDirectories(nestedDir)
        Files.writeString(nestedDir.resolve("petrov.iv.csv"), "hash\nabc\n")
        val archivePath = tempDir.resolve("output.zip")

        val result = Zip().archive(outputDir, archivePath)

        assertEquals(archivePath, result.path)
        assertEquals(1, result.entries)
        ZipFile(archivePath.toFile()).use { zip ->
            val entry = zip.getEntry("Аутсорсинг/ООО Ромашка/petrov.iv.csv")
            assertEquals("hash\nabc\n", zip.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `existing archive path fails fast`() {
        val outputDir = tempDir.resolve("output")
        Files.createDirectory(outputDir)
        val archivePath = tempDir.resolve("output.zip")
        Files.writeString(archivePath, "already exists")

        val error = assertFailsWith<VpException> {
            Zip().archive(outputDir, archivePath)
        }

        assertEquals(ExitCode.ARCHIVE_EXISTS, error.exitCode)
    }

    @Test
    fun `archive creation failure maps to archive error`() {
        val outputDir = tempDir.resolve("output")
        Files.createDirectory(outputDir)
        val notDirectory = tempDir.resolve("not-a-dir")
        Files.writeString(notDirectory, "file")

        val error = assertFailsWith<VpException> {
            Zip().archive(outputDir, notDirectory.resolve("output.zip"))
        }

        assertEquals(ExitCode.ARCHIVE_ERROR, error.exitCode)
    }
}
