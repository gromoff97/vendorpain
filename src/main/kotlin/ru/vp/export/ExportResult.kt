package ru.vp.export

import java.nio.file.Path

data class ExportResult(
    val outputDir: Path,
    val archivePath: Path?,
    val filesWritten: Int,
)
