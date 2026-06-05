package ru.vp.paths

import ru.vp.error.ExitCode
import ru.vp.error.VpException

class PathValidator {
    fun validateSegment(segment: String): String {
        if (segment.isBlank()) {
            throw invalid(segment, "path segment must not be blank")
        }
        if (segment.trim() != segment) {
            throw invalid(segment, "path segment must not have leading or trailing whitespace")
        }
        if (segment.endsWith(".")) {
            throw invalid(segment, "path segment must not end with a dot")
        }
        if (segment.any { it in forbiddenCharacters }) {
            throw invalid(segment, "path segment contains a forbidden character")
        }
        if (segment.any { it.code in 0..31 }) {
            throw invalid(segment, "path segment contains a control character")
        }
        if (segment.isWindowsReservedName()) {
            throw invalid(segment, "path segment is a Windows reserved name")
        }

        return segment
    }

    private fun String.isWindowsReservedName(): Boolean {
        val name = substringBefore('.').uppercase()
        return name in reservedWindowsNames
    }

    private fun invalid(segment: String, reason: String): VpException =
        VpException(ExitCode.INVALID_OUTPUT_PATH, "$reason: '$segment'")

    private companion object {
        private val forbiddenCharacters = setOf('/', '\\', '<', '>', ':', '"', '|', '?', '*')
        private val reservedWindowsNames = buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            (1..9).forEach { index ->
                add("COM$index")
                add("LPT$index")
            }
        }
    }
}
