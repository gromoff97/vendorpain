package ru.vp.awesomegraphs

data class CsvResult(
    val bytes: ByteArray,
    val url: String,
    val status: Int,
    val type: String?,
    val tries: Int,
)
