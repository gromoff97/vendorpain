package ru.vp.config

data class Options(
    val baseUrl: String,
    val token: String,
    val sinceDate: String,
    val untilDate: String,
    val merges: String,
    val order: String,
    val outputDir: String,
    val archive: Boolean,
    val debug: Boolean,
    val timeoutSeconds: Int,
    val retries: Int,
)
