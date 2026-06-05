package ru.vp.export

import java.nio.file.Path

data class ExportResult(
    val dir: Path,
    val zip: Path?,
    val files: Int,
)
