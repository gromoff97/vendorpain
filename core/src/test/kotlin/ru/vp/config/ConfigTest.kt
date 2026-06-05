package ru.vp.config

import org.junit.jupiter.api.io.TempDir
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loads valid yaml config`() {
        val config = load(validYaml())

        assertEquals("https://stash.nspk.ru/rest/awesome-graphs-api/latest", config.options.baseUrl)
        assertEquals("basic", config.options.auth.method)
        assertEquals("petrov.iv", config.options.auth.username)
        assertEquals("secret-password", config.options.auth.password)
        assertEquals("2026-03-04", config.options.sinceDate)
        assertEquals("exclude", config.options.merges)
        assertEquals(false, config.options.insecure)
        assertEquals(listOf("Аутсорсинг", "ООО Ромашка"), config.exports.single().path)
        assertEquals(listOf("petrov.iv", "ivanov.ia"), config.exports.single().slugs)
    }

    @Test
    fun `missing required option fails as invalid schema`() {
        val error = assertFailsWith<VpException> {
            load(validYaml().replace(authYaml(), ""))
        }

        assertEquals(ExitCode.INVALID_CONFIG_SCHEMA, error.exitCode)
    }

    @Test
    fun `missing insecure option fails as invalid schema`() {
        val error = assertFailsWith<VpException> {
            load(validYaml().replace("  insecure: false\n", ""))
        }

        assertEquals(ExitCode.INVALID_CONFIG_SCHEMA, error.exitCode)
    }

    @Test
    fun `token auth is still supported explicitly`() {
        val config = load(
            validYaml().replace(
                authYaml(),
                """
  auth:
    method: "token"
    token: "secret-token"
    username: null
    password: null
""",
            ),
        )

        assertEquals("token", config.options.auth.method)
        assertEquals("secret-token", config.options.auth.token)
    }

    @Test
    fun `invalid yaml syntax fails as invalid yaml`() {
        val file = tempDir.resolve("broken.yml")
        file.writeText("options:\n  baseUrl: [")

        val error = assertFailsWith<VpException> {
            Loader().load(file)
        }

        assertEquals(ExitCode.INVALID_YAML, error.exitCode)
    }

    @Test
    fun `date range must be ordered and no longer than 366 days`() {
        assertValidationCode(
            yaml = validYaml().replace("sinceDate: \"2026-03-04\"", "sinceDate: \"2026-06-05\""),
            expected = ExitCode.INVALID_DATE_RANGE,
        )
        assertValidationCode(
            yaml = validYaml()
                .replace("sinceDate: \"2026-03-04\"", "sinceDate: \"2025-01-01\"")
                .replace("untilDate: \"2026-06-04\"", "untilDate: \"2026-01-02\""),
            expected = ExitCode.INVALID_DATE_RANGE,
        )
    }

    @Test
    fun `invalid option values fail`() {
        assertValidationCode(
            yaml = validYaml().replace("merges: \"exclude\"", "merges: \"bad\""),
            expected = ExitCode.INVALID_OPTION_VALUE,
        )
        assertValidationCode(
            yaml = validYaml().replace("order: \"newest\"", "order: \"middle\""),
            expected = ExitCode.INVALID_OPTION_VALUE,
        )
        assertValidationCode(
            yaml = validYaml().replace("timeoutSeconds: 60", "timeoutSeconds: 0"),
            expected = ExitCode.INVALID_OPTION_VALUE,
        )
        assertValidationCode(
            yaml = validYaml().replace("retries: 0", "retries: -1"),
            expected = ExitCode.INVALID_OPTION_VALUE,
        )
        assertValidationCode(
            yaml = validYaml().replace("method: \"basic\"", "method: \"cookie\""),
            expected = ExitCode.INVALID_OPTION_VALUE,
        )
    }

    @Test
    fun `invalid base url fails with invalid url code`() {
        assertValidationCode(
            yaml = validYaml().replace(
                "baseUrl: \"https://stash.nspk.ru/rest/awesome-graphs-api/latest\"",
                "baseUrl: \"not a url\"",
            ),
            expected = ExitCode.INVALID_URL,
        )
    }

    @Test
    fun `exports must be non empty`() {
        assertValidationCode(
            yaml = validYaml().substringBefore("exports:") + "exports: []\n",
            expected = ExitCode.INVALID_CONFIG_SCHEMA,
        )
    }

    @Test
    fun `empty export path is allowed`() {
        val config = load(validYaml().replace("path: [\"Аутсорсинг\", \"ООО Ромашка\"]", "path: []"))

        assertEquals(emptyList(), config.exports.single().path)
    }

    @Test
    fun `loads ssh tunnel options`() {
        val config = load(
            validYaml().replace(
                "  retries: 0",
                """  retries: 0
  ssh:
    host: "jump.example"
    port: 2222
    user: "anton"
    password: "secret"
    privateKeyPath: "/home/anton/.ssh/id_ed25519"
    passphrase: "phrase"
    knownHostsPath: "/home/anton/.ssh/known_hosts"
    strictHostKeyChecking: true""",
            ),
        )

        val ssh = config.options.ssh
        assertEquals("jump.example", ssh?.host)
        assertEquals(2222, ssh?.port)
        assertEquals("anton", ssh?.user)
        assertEquals("secret", ssh?.password)
        assertEquals("/home/anton/.ssh/id_ed25519", ssh?.privateKeyPath)
        assertEquals("phrase", ssh?.passphrase)
        assertEquals("/home/anton/.ssh/known_hosts", ssh?.knownHostsPath)
        assertEquals(true, ssh?.strictHostKeyChecking)
    }

    @Test
    fun `duplicate slugs across entire config fail`() {
        val yaml = validYaml() + """

  - path: ["Другой vendor"]
    slugs:
      - petrov.iv
""".trimEnd()

        assertValidationCode(yaml, ExitCode.INVALID_OUTPUT_PATH)
    }

    @Test
    fun `duplicate export paths fail`() {
        val yaml = validYaml() + """

  - path: ["Аутсорсинг", "ООО Ромашка"]
    slugs:
      - sidorov.sp
""".trimEnd()

        assertValidationCode(yaml, ExitCode.INVALID_OUTPUT_PATH)
    }

    @Test
    fun `slug with leading or trailing whitespace fails`() {
        assertValidationCode(
            yaml = validYaml().replace("- petrov.iv", "- \" petrov.iv\""),
            expected = ExitCode.INVALID_CONFIG_SCHEMA,
        )
    }

    private fun load(yaml: String): Config {
        val file = tempDir.resolve("vendors.yml")
        file.writeText(yaml)
        return Loader().load(file)
    }

    private fun assertValidationCode(yaml: String, expected: ExitCode) {
        val error = assertFailsWith<VpException> { load(yaml) }
        assertEquals(expected, error.exitCode)
    }

    private fun validYaml(): String = """
options:
  baseUrl: "https://stash.nspk.ru/rest/awesome-graphs-api/latest"
  auth:
    method: "basic"
    username: "petrov.iv"
    password: "secret-password"
    token: null
  sinceDate: "2026-03-04"
  untilDate: "2026-06-04"
  merges: "exclude"
  order: "newest"
  outputDir: "output"
  archive: true
  debug: false
  insecure: false
  timeoutSeconds: 60
  retries: 0

exports:
  - path: ["Аутсорсинг", "ООО Ромашка"]
    slugs:
      - petrov.iv
      - ivanov.ia
""".trimStart()

    private fun authYaml(): String =
        "  auth:\n" +
            "    method: \"basic\"\n" +
            "    username: \"petrov.iv\"\n" +
            "    password: \"secret-password\"\n" +
            "    token: null\n"
}
