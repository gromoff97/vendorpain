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

class Zip {
    fun archive(dir: Path, zip: Path): ArchiveResult {
        if (Files.exists(zip)) {
            throw VpException(ExitCode.ARCHIVE_EXISTS, "archive already exists: $zip")
        }

        try {
            zip.parent?.let(Files::createDirectories)
            var entries = 0
            ZipOutputStream(Files.newOutputStream(zip, StandardOpenOption.CREATE_NEW)).use { out ->
                Files.walk(dir).use { paths ->
                    paths
                        .asSequence()
                        .filter(Files::isRegularFile)
                        .sortedBy { it.toString() }
                        .forEach { file ->
                            out.putNextEntry(ZipEntry(entry(dir, file)))
                            Files.copy(file, out)
                            out.closeEntry()
                            entries += 1
                        }
                }
            }
            return ArchiveResult(path = zip, entries = entries)
        } catch (e: IOException) {
            throw VpException(ExitCode.ARCHIVE_ERROR, "failed to create archive: $zip", e)
        }
    }

    private fun entry(dir: Path, file: Path): String =
        dir.relativize(file).joinToString("/") { it.toString() }
}
