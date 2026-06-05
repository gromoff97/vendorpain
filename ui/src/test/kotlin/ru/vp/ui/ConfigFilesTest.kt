package ru.vp.ui

import org.junit.jupiter.api.io.TempDir
import ru.vp.bitbucket.BitbucketUser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigFilesTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `form can be saved and loaded`() {
        val file = tempDir.resolve("vp.yml")
        val original = ExportForm(
            bitbucketBaseUrl = "https://stash.example",
            graphsBaseUrl = "https://stash.example/rest/awesome-graphs-api/latest",
            authMethod = "basic",
            username = "admin",
            password = "secret",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "include",
            order = "oldest",
            outputDir = tempDir.resolve("out").toString(),
            archive = true,
            debug = true,
            insecure = true,
            timeoutSeconds = "45",
            retries = "2",
            sshEnabled = true,
            sshHost = "vdi-wsl",
            sshPort = "22",
            sshUser = "anton",
            sshPassword = "ssh-secret",
            sshStrictHostKeyChecking = true,
            groups = listOf(
                UiExportGroup(
                    name = "Vendor A",
                    pathText = "Outsource / Vendor A",
                    users = listOf(BitbucketUser("petrov.iv", "petrov.iv", "Петров", "petrov@example.test", true)),
                ),
            ),
        )

        ConfigFiles().save(file, original)
        val loaded = ConfigFiles().load(file)

        assertEquals(original.config(), loaded.config())
        assertEquals("Outsource / Vendor A", loaded.groups.single().pathText)
    }
}
