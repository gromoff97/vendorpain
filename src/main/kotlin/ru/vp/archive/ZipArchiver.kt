package ru.vp.archive

import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

class ZipArchiver {
    fun archive(outputDir: Path, archivePath: Path): ArchiveResult {
        if (Files.exists(archivePath)) {
            throw VpException(ExitCode.ARCHIVE_EXISTS, "archive already exists: $archivePath")
        }

        try {
            archivePath.parent?.let(Files::createDirectories)
            var entriesWritten = 0
            ZipOutputStream(Files.newOutputStream(archivePath, StandardOpenOption.CREATE_NEW)).use { zip ->
                Files.walk(outputDir).use { paths ->
                    paths
                        .asSequence()
                        .filter(Files::isRegularFile)
                        .sortedBy { it.toString() }
                        .forEach { file ->
                            zip.putNextEntry(ZipEntry(zipEntryName(outputDir, file)))
                            Files.copy(file, zip)
                            zip.closeEntry()
                            entriesWritten += 1
                        }
                }
            }
            return ArchiveResult(archivePath = archivePath, entriesWritten = entriesWritten)
        } catch (e: IOException) {
            throw VpException(ExitCode.ARCHIVE_ERROR, "failed to create archive: $archivePath", e)
        }
    }

    private fun zipEntryName(outputDir: Path, file: Path): String =
        outputDir.relativize(file).joinToString("/") { it.toString() }
}
