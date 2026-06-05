package ru.vp.paths

import java.nio.file.Path

data class OutputFilePlan(
    val exportPath: List<String>,
    val slug: String,
    val directory: Path,
    val csvPath: Path,
)
