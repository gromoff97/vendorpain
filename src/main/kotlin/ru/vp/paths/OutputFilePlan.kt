package ru.vp.paths

import java.nio.file.Path

data class OutputFilePlan(
    val path: List<String>,
    val slug: String,
    val dir: Path,
    val file: Path,
)
