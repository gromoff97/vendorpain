package ru.vp.ui

import ru.vp.bitbucket.BitbucketUser
import ru.vp.config.Auth
import ru.vp.config.Config
import ru.vp.config.Loader
import ru.vp.config.Writer
import java.nio.file.Path

interface ConfigFileStore {
    fun save(path: Path, form: ExportForm)
    fun load(path: Path): ExportForm
}

class ConfigFiles(
    private val loader: Loader = Loader(),
    private val writer: Writer = Writer(),
) : ConfigFileStore {
    override fun save(path: Path, form: ExportForm) {
        writer.write(path, form.config())
    }

    override fun load(path: Path): ExportForm = fromConfig(loader.load(path))

    private fun fromConfig(config: Config): ExportForm {
        val options = config.options
        val ssh = options.ssh
        return ExportForm(
            graphsBaseUrl = options.baseUrl,
            authMethod = options.auth.method,
            username = options.auth.username.orEmpty(),
            password = options.auth.password.orEmpty(),
            token = options.auth.token.orEmpty(),
            sinceDate = options.sinceDate,
            untilDate = options.untilDate,
            merges = options.merges,
            order = options.order,
            outputDir = options.outputDir,
            archive = options.archive,
            debug = options.debug,
            insecure = options.insecure,
            timeoutSeconds = options.timeoutSeconds.toString(),
            retries = options.retries.toString(),
            sshEnabled = ssh != null,
            sshHost = ssh?.host.orEmpty(),
            sshPort = ssh?.port?.toString() ?: "22",
            sshUser = ssh?.user.orEmpty(),
            sshPassword = ssh?.password.orEmpty(),
            sshPrivateKeyPath = ssh?.privateKeyPath.orEmpty(),
            sshPassphrase = ssh?.passphrase.orEmpty(),
            sshKnownHostsPath = ssh?.knownHostsPath.orEmpty(),
            sshStrictHostKeyChecking = ssh?.strictHostKeyChecking ?: false,
            groups = config.exports.mapIndexed { index, group ->
                UiExportGroup(
                    name = group.path.lastOrNull() ?: "Group ${index + 1}",
                    pathText = group.path.joinToString(" / "),
                    users = group.slugs.map { slug -> BitbucketUser(slug, slug, slug, null, true) },
                )
            }.ifEmpty { listOf(UiExportGroup()) },
        )
    }
}
