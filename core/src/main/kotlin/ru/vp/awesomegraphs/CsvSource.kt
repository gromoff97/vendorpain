package ru.vp.awesomegraphs

fun interface CsvSource : AutoCloseable {
    fun downloadCsv(slug: String): CsvResult

    override fun close() = Unit
}
