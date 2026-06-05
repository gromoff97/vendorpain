package ru.vp.paths

import ru.vp.error.ExitCode
import ru.vp.error.VpException

class PathRules {
    fun check(segment: String): String = segment.also { value ->
        rules.firstOrNull { it.first(value) }?.let { throw invalid(value, it.second) }
    }

    private fun invalid(segment: String, reason: String): VpException =
        VpException(ExitCode.INVALID_OUTPUT_PATH, "$reason: '$segment'")

    private companion object {
        private val badChars = setOf('/', '\\', '<', '>', ':', '"', '|', '?', '*')
        private val reserved = buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            (1..9).forEach {
                add("COM$it")
                add("LPT$it")
            }
        }
        private val rules = listOf(
            String::isBlank to "path segment must not be blank",
            { it: String -> it.trim() != it } to "path segment must not have leading or trailing whitespace",
            { it: String -> it.endsWith(".") } to "path segment must not end with a dot",
            { it: String -> it.any(badChars::contains) } to "path segment contains a forbidden character",
            { it: String -> it.any { char -> char.code in 0..31 } } to "path segment contains a control character",
            { it: String -> reserved(it) } to "path segment is a Windows reserved name",
        )

        private fun reserved(value: String): Boolean = value.substringBefore('.').uppercase() in reserved
    }
}
