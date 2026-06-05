package ru.vp.awesomegraphs

import ru.vp.error.ExitCode
import ru.vp.error.VpException

class CsvGuard {
    fun validate(contentType: String?, bytes: ByteArray) {
        if (looksLikeHtml(contentType, bytes)) {
            throw VpException(ExitCode.NON_CSV_RESPONSE, "Awesome Graphs returned HTML instead of CSV")
        }
    }

    fun looksLikeHtml(contentType: String?, bytes: ByteArray): Boolean {
        if (contentType?.lowercase()?.contains("html") == true) {
            return true
        }

        val prefix = bytes
            .decodeToString(endIndex = minOf(bytes.size, 256))
            .trimStart()
            .lowercase()

        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
    }
}
