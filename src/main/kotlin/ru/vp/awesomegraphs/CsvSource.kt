package ru.vp.awesomegraphs

fun interface CsvSource {
    fun downloadCsv(slug: String): CsvResult
}
