package ru.vp.archive

import java.nio.file.Path

data class ArchiveResult(
    val archivePath: Path,
    val entriesWritten: Int,
)
