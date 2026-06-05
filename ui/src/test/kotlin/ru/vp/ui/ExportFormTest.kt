package ru.vp.ui

import ru.vp.bitbucket.BitbucketUser
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportFormTest {
    @Test
    fun `builds grouped config from export groups`() {
        val form = ExportForm(
            graphsBaseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            authMethod = "basic",
            username = "admin",
            password = "secret-password",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = "output",
            archive = true,
            debug = false,
            insecure = true,
            timeoutSeconds = "60",
            retries = "0",
            groups = listOf(
                UiExportGroup(
                    name = "Vendor A",
                    pathText = "Outsource / Vendor A",
                    users = listOf(BitbucketUser("petrov.iv", "petrov.iv", "Петров", null, true)),
                ),
                UiExportGroup(
                    name = "Vendor B",
                    pathText = "Outsource / Vendor B / Team 1",
                    users = listOf(BitbucketUser("ivanov.ia", "ivanov.ia", "Иванов", null, true)),
                ),
            ),
        )

        val config = form.config()

        assertEquals(true, config.options.insecure)
        assertEquals(listOf("Outsource", "Vendor A"), config.exports[0].path)
        assertEquals(listOf("petrov.iv"), config.exports[0].slugs)
        assertEquals(listOf("Outsource", "Vendor B", "Team 1"), config.exports[1].path)
        assertEquals(listOf("ivanov.ia"), config.exports[1].slugs)
    }

    @Test
    fun `select adds user to active group only once`() {
        val state = AppState()
        state.form = state.form.copy(groups = listOf(UiExportGroup(name = "Vendor A", pathText = "Vendor A")))

        state.select(BitbucketUser("petrov.iv", "petrov.iv", "Петров", null, true))
        state.select(BitbucketUser("petrov.iv", "petrov.iv", "Петров", null, true))

        assertEquals(listOf("petrov.iv"), state.form.groups.single().users.map { it.slug })
    }

    @Test
    fun `builds core config from selected users`() {
        val form = ExportForm(
            graphsBaseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            authMethod = "basic",
            username = "petrov.iv",
            password = "secret-password",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = "output",
            archive = true,
            debug = false,
            insecure = false,
            timeoutSeconds = "60",
            retries = "0",
            sshEnabled = true,
            sshHost = "jump.example",
            sshPort = "2222",
            sshUser = "anton",
            sshPassword = "secret",
            sshPrivateKeyPath = "/home/anton/.ssh/id_ed25519",
            sshPassphrase = "phrase",
            sshKnownHostsPath = "/home/anton/.ssh/known_hosts",
            sshStrictHostKeyChecking = true,
            users = listOf(
                BitbucketUser("petrov.iv", "petrov.iv", "Петров Игорь", "petrov.iv@nspk.ru", true),
                BitbucketUser("ivanov.ia", "ivanov.ia", "Иванов Игорь", "ivanov.ia@nspk.ru", true),
            ),
        )

        val config = form.config()

        assertEquals("https://stash.example/rest/awesome-graphs-api/latest", config.options.baseUrl)
        assertEquals("basic", config.options.auth.method)
        assertEquals("petrov.iv", config.options.auth.username)
        assertEquals("secret-password", config.options.auth.password)
        assertEquals("output", config.options.outputDir)
        assertEquals(true, config.options.archive)
        assertEquals("jump.example", config.options.ssh?.host)
        assertEquals(2222, config.options.ssh?.port)
        assertEquals("anton", config.options.ssh?.user)
        assertEquals("secret", config.options.ssh?.password)
        assertEquals(listOf("petrov.iv", "ivanov.ia"), config.exports.single().slugs)
        assertEquals(emptyList(), config.exports.single().path)
    }

    @Test
    fun `builds token auth without stale basic credentials`() {
        val config = ExportForm(
            graphsBaseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            authMethod = "token",
            username = "stale-user",
            password = "stale-password",
            token = "secret-token",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            outputDir = "output",
            users = listOf(BitbucketUser("petrov.iv", "petrov.iv", "Петров Игорь", null, true)),
        ).config()

        assertEquals("token", config.options.auth.method)
        assertEquals(null, config.options.auth.username)
        assertEquals(null, config.options.auth.password)
        assertEquals("secret-token", config.options.auth.token)
    }
}
