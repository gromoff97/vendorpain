package ru.vp.paths

import java.nio.file.Path

data class ExportPlan(
    val outputDir: Path,
    val archivePath: Path?,
    val files: List<OutputFilePlan>,
)
