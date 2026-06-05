package ru.vp.archive

import java.nio.file.Path

data class ArchiveResult(
    val path: Path,
    val entries: Int,
)
