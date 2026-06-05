package ru.vp.awesomegraphs

data class CsvDownloadResult(
    val bytes: ByteArray,
    val requestUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val attempts: Int,
)
