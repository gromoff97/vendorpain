package ru.vp.awesomegraphs

fun interface CsvDownloader {
    fun downloadCsv(slug: String): CsvDownloadResult
}
