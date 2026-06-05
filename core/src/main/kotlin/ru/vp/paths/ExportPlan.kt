package ru.vp.paths

import java.nio.file.Path

data class ExportPlan(
    val dir: Path,
    val zip: Path?,
    val files: List<OutputFilePlan>,
)
