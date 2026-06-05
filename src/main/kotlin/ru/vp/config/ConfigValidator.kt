package ru.vp.config

import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

class ConfigValidator {
    fun validate(config: VpConfig): VpConfig {
        validateOptions(config.options)
        validateExports(config.exports)
        return config
    }

    private fun validateOptions(options: OptionsConfig) {
        requireNonBlank(options.baseUrl, "options.baseUrl")
        requireNonBlank(options.token, "options.token")
        requireNonBlank(options.outputDir, "options.outputDir")

        val since = parseDate(options.sinceDate, "options.sinceDate")
        val until = parseDate(options.untilDate, "options.untilDate")
        if (since.isAfter(until)) {
            throw VpException(ExitCode.INVALID_DATE_RANGE, "options.sinceDate must be before or equal to options.untilDate")
        }

        val inclusiveDays = ChronoUnit.DAYS.between(since, until) + 1
        if (inclusiveDays > 366) {
            throw VpException(ExitCode.INVALID_DATE_RANGE, "date period must not exceed 366 days")
        }

        if (options.merges !in setOf("include", "exclude", "only")) {
            throw VpException(ExitCode.INVALID_OPTION_VALUE, "options.merges must be one of include, exclude, only")
        }
        if (options.order !in setOf("newest", "oldest")) {
            throw VpException(ExitCode.INVALID_OPTION_VALUE, "options.order must be one of newest, oldest")
        }
        if (options.timeoutSeconds <= 0) {
            throw VpException(ExitCode.INVALID_OPTION_VALUE, "options.timeoutSeconds must be a positive integer")
        }
        if (options.retries < 0) {
            throw VpException(ExitCode.INVALID_OPTION_VALUE, "options.retries must be a non-negative integer")
        }
    }

    private fun validateExports(exports: List<ExportConfig>) {
        if (exports.isEmpty()) {
            throw VpException(ExitCode.INVALID_CONFIG_SCHEMA, "exports must not be empty")
        }

        val seenPaths = mutableSetOf<List<String>>()
        val seenSlugs = mutableSetOf<String>()

        exports.forEachIndexed { index, export ->
            if (export.path.isEmpty()) {
                throw VpException(ExitCode.INVALID_CONFIG_SCHEMA, "exports[$index].path must not be empty")
            }
            if (export.slugs.isEmpty()) {
                throw VpException(ExitCode.INVALID_CONFIG_SCHEMA, "exports[$index].slugs must not be empty")
            }
            if (!seenPaths.add(export.path)) {
                throw VpException(ExitCode.INVALID_OUTPUT_PATH, "duplicate export path: ${export.path.joinToString("/")}")
            }

            export.slugs.forEach { slug ->
                if (slug.isEmpty() || slug.trim() != slug) {
                    throw VpException(ExitCode.INVALID_CONFIG_SCHEMA, "slug must be non-empty without leading or trailing whitespace")
                }
                if (!seenSlugs.add(slug)) {
                    throw VpException(ExitCode.INVALID_OUTPUT_PATH, "duplicate slug: $slug")
                }
            }
        }
    }

    private fun parseDate(value: String, field: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw VpException(ExitCode.INVALID_DATE_RANGE, "$field must be an ISO date in YYYY-MM-DD format", e)
        }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw VpException(ExitCode.INVALID_CONFIG_SCHEMA, "$field must not be blank")
        }
    }
}
