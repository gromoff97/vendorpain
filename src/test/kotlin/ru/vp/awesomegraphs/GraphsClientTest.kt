package ru.vp.awesomegraphs

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GraphsClientTest {
    @Test
    fun `downloads csv with expected URL query and bearer token`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(csvResponse("hash,date\nabc,2026-06-04\n"))
            val baseUrl = server.url("/rest/awesome-graphs-api/latest").toString().removeSuffix("/")

            val result = client(baseUrl = baseUrl).downloadCsv("petrov.iv")

            assertEquals("hash,date\nabc,2026-06-04\n", result.bytes.toString(Charsets.UTF_8))
            val request = server.takeRequest()
            assertEquals(
                "/rest/awesome-graphs-api/latest/users/petrov.iv/commits/export/csv" +
                    "?sinceDate=2026-03-04&untilDate=2026-06-04&merges=exclude&order=newest",
                request.url.encodedPath + "?" + request.url.encodedQuery,
            )
            assertEquals("Bearer secret-token", request.headers["Authorization"])
        }
    }

    @Test
    fun `base URL may have a trailing slash`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(csvResponse("hash\n"))
            val baseUrl = server.url("/rest/awesome-graphs-api/latest/").toString()

            client(baseUrl = baseUrl).downloadCsv("petrov.iv")

            val request = server.takeRequest()
            assertEquals("/rest/awesome-graphs-api/latest/users/petrov.iv/commits/export/csv", request.url.encodedPath)
        }
    }

    @Test
    fun `empty csv body is successful`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(csvResponse(""))

            val result = client(baseUrl = server.url("/api").toString()).downloadCsv("petrov.iv")

            assertEquals("", result.bytes.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `http 200 html body fails as non csv`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .body("<html>login</html>")
                    .build(),
            )

            val error = assertFailsWith<VpException> {
                client(baseUrl = server.url("/api").toString()).downloadCsv("petrov.iv")
            }

            assertEquals(ExitCode.NON_CSV_RESPONSE, error.exitCode)
        }
    }

    @Test
    fun `http 401 and 403 fail without retry`() {
        assertHttpCodeMapsTo(401, ExitCode.AUTHENTICATION_FAILED)
        assertHttpCodeMapsTo(403, ExitCode.PERMISSION_DENIED)
    }

    @Test
    fun `api-like 404 maps to user not found`() {
        assertHttpCodeMapsTo(
            code = 404,
            expected = ExitCode.USER_NOT_FOUND,
            contentType = "application/json",
            body = """{"message":"user not found"}""",
        )
    }

    @Test
    fun `html 404 maps to endpoint not found`() {
        assertHttpCodeMapsTo(
            code = 404,
            expected = ExitCode.ENDPOINT_NOT_FOUND,
            contentType = "text/html",
            body = "<html>not found</html>",
        )
    }

    @Test
    fun `retries 5xx and returns successful csv`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response(code = 500, contentType = "text/plain", body = "temporary"))
            server.enqueue(csvResponse("hash\nabc\n"))
            val sleeps = mutableListOf<Long>()

            val result = client(
                baseUrl = server.url("/api").toString(),
                retries = 1,
                sleepMillis = sleeps::add,
            ).downloadCsv("petrov.iv")

            assertEquals("hash\nabc\n", result.bytes.toString(Charsets.UTF_8))
            assertEquals(2, server.requestCount)
            assertEquals(listOf(1_000L), sleeps)
        }
    }

    @Test
    fun `5xx after retries fails as server error`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response(code = 500, contentType = "text/plain", body = "temporary"))
            server.enqueue(response(code = 502, contentType = "text/plain", body = "temporary"))

            val error = assertFailsWith<VpException> {
                client(
                    baseUrl = server.url("/api").toString(),
                    retries = 1,
                    sleepMillis = {},
                ).downloadCsv("petrov.iv")
            }

            assertEquals(ExitCode.SERVER_ERROR, error.exitCode)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `network failure maps to network error`() {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api").toString()
        server.close()

        val error = assertFailsWith<VpException> {
            client(baseUrl = baseUrl, timeoutSeconds = 1, retries = 0).downloadCsv("petrov.iv")
        }

        assertEquals(ExitCode.NETWORK_ERROR, error.exitCode)
    }

    private fun assertHttpCodeMapsTo(
        code: Int,
        expected: ExitCode,
        contentType: String = "text/plain",
        body: String = "error",
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response(code = code, contentType = contentType, body = body))

            val error = assertFailsWith<VpException> {
                client(baseUrl = server.url("/api").toString(), retries = 2).downloadCsv("petrov.iv")
            }

            assertEquals(expected, error.exitCode)
            assertEquals(1, server.requestCount)
        }
    }

    private fun client(
        baseUrl: String,
        timeoutSeconds: Int = 5,
        retries: Int = 0,
        sleepMillis: (Long) -> Unit = {},
    ): GraphsClient = GraphsClient(
        options = Options(
            baseUrl = baseUrl,
            token = "secret-token",
            sinceDate = "2026-03-04",
            untilDate = "2026-06-04",
            merges = "exclude",
            order = "newest",
            outputDir = "output",
            archive = false,
            debug = false,
            timeoutSeconds = timeoutSeconds,
            retries = retries,
        ),
        sleep = sleepMillis,
    )

    private fun csvResponse(body: String): MockResponse =
        response(code = 200, contentType = "text/csv; charset=utf-8", body = body)

    private fun response(code: Int, contentType: String, body: String): MockResponse =
        MockResponse.Builder()
            .code(code)
            .setHeader("Content-Type", contentType)
            .body(body)
            .build()
}
