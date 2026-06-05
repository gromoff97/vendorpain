package ru.vp.export

import java.nio.file.Path

data class ExportProgress(
    val done: Int,
    val total: Int,
    val slug: String?,
    val file: Path?,
)
