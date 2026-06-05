package ru.vp.config

import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

class Rules {
    fun validate(config: Config): Config = config.also {
        options(it.options)
        exports(it.exports)
    }

    private fun options(o: Options) {
        listOf(
            o.baseUrl to "options.baseUrl",
            o.outputDir to "options.outputDir",
        ).forEach { (value, field) -> schema(value.isNotBlank(), "$field must not be blank") }
        auth(o.auth)

        val since = date(o.sinceDate, "options.sinceDate")
        val until = date(o.untilDate, "options.untilDate")

        dates(!since.isAfter(until), "options.sinceDate must be before or equal to options.untilDate")
        dates(ChronoUnit.DAYS.between(since, until) < 366, "date period must not exceed 366 days")
        option(o.merges in merges, "options.merges must be one of include, exclude, only")
        option(o.order in orders, "options.order must be one of newest, oldest")
        option(o.timeoutSeconds > 0, "options.timeoutSeconds must be a positive integer")
        option(o.retries >= 0, "options.retries must be a non-negative integer")
        o.ssh?.let(::ssh)
    }

    private fun exports(groups: List<Group>) {
        schema(groups.isNotEmpty(), "exports must not be empty")

        val seenPaths = mutableSetOf<List<String>>()
        val seenSlugs = mutableSetOf<String>()

        groups.forEachIndexed { i, group ->
            schema(group.slugs.isNotEmpty(), "exports[$i].slugs must not be empty")
            path(seenPaths.add(group.path), "duplicate export path: ${group.path.joinToString("/")}")

            group.slugs.forEach { slug ->
                schema(slug.isNotEmpty() && slug.trim() == slug, "slug must be non-empty without leading or trailing whitespace")
                path(seenSlugs.add(slug), "duplicate slug: $slug")
            }
        }
    }

    private fun date(value: String, field: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw VpException(ExitCode.INVALID_DATE_RANGE, "$field must be an ISO date in YYYY-MM-DD format", e)
        }

    private fun schema(ok: Boolean, message: String) = need(ok, ExitCode.INVALID_CONFIG_SCHEMA, message)
    private fun dates(ok: Boolean, message: String) = need(ok, ExitCode.INVALID_DATE_RANGE, message)
    private fun option(ok: Boolean, message: String) = need(ok, ExitCode.INVALID_OPTION_VALUE, message)
    private fun path(ok: Boolean, message: String) = need(ok, ExitCode.INVALID_OUTPUT_PATH, message)

    private fun need(ok: Boolean, code: ExitCode, message: String) {
        if (!ok) throw VpException(code, message)
    }

    private fun ssh(value: Ssh) {
        schema(value.host.isNotBlank(), "options.ssh.host must not be blank")
        option(value.port in 1..65535, "options.ssh.port must be between 1 and 65535")
        schema(value.user.isNotBlank(), "options.ssh.user must not be blank")
        schema(
            !value.password.isNullOrBlank() || !value.privateKeyPath.isNullOrBlank(),
            "options.ssh.password or options.ssh.privateKeyPath must be set",
        )
    }

    private fun auth(value: Auth) {
        schema(value.method.isNotBlank(), "options.auth.method must not be blank")
        option(value.method in authMethods, "options.auth.method must be one of basic, token")

        when (value.method) {
            Auth.BASIC -> {
                schema(!value.username.isNullOrBlank(), "options.auth.username must not be blank for basic auth")
                schema(!value.password.isNullOrBlank(), "options.auth.password must not be blank for basic auth")
                schema(value.token.isNullOrBlank(), "options.auth.token must be blank for basic auth")
            }
            Auth.TOKEN -> {
                schema(!value.token.isNullOrBlank(), "options.auth.token must not be blank for token auth")
                schema(value.username.isNullOrBlank(), "options.auth.username must be blank for token auth")
                schema(value.password.isNullOrBlank(), "options.auth.password must be blank for token auth")
            }
        }
    }

    private companion object {
        private val merges = setOf("include", "exclude", "only")
        private val orders = setOf("newest", "oldest")
        private val authMethods = setOf(Auth.BASIC, Auth.TOKEN)
    }
}
