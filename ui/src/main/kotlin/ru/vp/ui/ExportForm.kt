package ru.vp.ui

import ru.vp.bitbucket.BitbucketUser
import ru.vp.config.Config
import ru.vp.config.Group
import ru.vp.config.Auth
import ru.vp.config.Options
import ru.vp.config.Ssh
import ru.vp.error.ExitCode
import ru.vp.error.VpException

data class UiExportGroup(
    val name: String = "Group",
    val pathText: String = "",
    val users: List<BitbucketUser> = emptyList(),
)

data class ExportForm(
    val bitbucketBaseUrl: String = "",
    val graphsBaseUrl: String = "",
    val authMethod: String = Auth.BASIC,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val sinceDate: String = "",
    val untilDate: String = "",
    val merges: String = "exclude",
    val order: String = "newest",
    val outputDir: String = "",
    val archive: Boolean = true,
    val debug: Boolean = false,
    val insecure: Boolean = false,
    val timeoutSeconds: String = "60",
    val retries: String = "0",
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUser: String = "",
    val sshPassword: String = "",
    val sshPrivateKeyPath: String = "",
    val sshPassphrase: String = "",
    val sshKnownHostsPath: String = "",
    val sshStrictHostKeyChecking: Boolean = false,
    val groups: List<UiExportGroup> = listOf(UiExportGroup()),
    val users: List<BitbucketUser> = emptyList(),
) {
    fun config(): Config =
        Config(
            options = Options(
                baseUrl = graphsBaseUrl,
                auth = auth(),
                sinceDate = sinceDate,
                untilDate = untilDate,
                merges = merges,
                order = order,
                outputDir = outputDir,
                archive = archive,
                debug = debug,
                insecure = insecure,
                timeoutSeconds = number(timeoutSeconds, "timeoutSeconds"),
                retries = number(retries, "retries"),
                ssh = ssh(),
            ),
            exports = exportGroups(),
        )

    private fun exportGroups(): List<Group> {
        val grouped = groups
            .filter { it.users.isNotEmpty() }
            .map { group ->
                Group(
                    path = path(group.pathText),
                    slugs = group.users.map(BitbucketUser::slug),
                )
            }

        return grouped.ifEmpty {
            listOf(Group(path = emptyList(), slugs = users.map(BitbucketUser::slug)))
        }
    }

    private fun path(value: String): List<String> =
        value.split("/")
            .map(String::trim)
            .filter(String::isNotEmpty)

    fun auth(): Auth =
        when (authMethod) {
            Auth.BASIC -> Auth(
                method = authMethod,
                username = username.ifBlank { null },
                password = password.ifBlank { null },
            )
            Auth.TOKEN -> Auth(
                method = authMethod,
                token = token.ifBlank { null },
            )
            else -> Auth(method = authMethod)
        }

    fun hasAuthInput(): Boolean =
        when (authMethod) {
            Auth.BASIC -> username.isNotBlank() && password.isNotBlank()
            Auth.TOKEN -> token.isNotBlank()
            else -> false
        }

    fun ssh(): Ssh? =
        if (sshEnabled) {
            Ssh(
                host = sshHost,
                port = number(sshPort, "sshPort"),
                user = sshUser,
                password = sshPassword.ifBlank { null },
                privateKeyPath = sshPrivateKeyPath.ifBlank { null },
                passphrase = sshPassphrase.ifBlank { null },
                knownHostsPath = sshKnownHostsPath.ifBlank { null },
                strictHostKeyChecking = sshStrictHostKeyChecking,
            )
        } else {
            null
        }

    private fun number(value: String, field: String): Int =
        value.toIntOrNull()
            ?: throw VpException(ExitCode.INVALID_NUMERIC_VALUE, "$field must be an integer")
}
