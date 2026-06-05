package ru.vp.bitbucket

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import ru.vp.config.Auth
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserSearchTest {
    @Test
    fun `searches users by filter with basic auth`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/json")
                    .body(
                        """
                        {
                          "values": [
                            {
                              "name": "petrov.iv",
                              "slug": "petrov.iv",
                              "displayName": "Петров Игорь Вадимович",
                              "emailAddress": "petrov.iv@nspk.ru",
                              "active": true
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                    .build(),
            )

            val users = BitbucketUsers(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                auth = Auth(method = "basic", username = "petrov.iv", password = "secret-password"),
            ).search("petrov", limit = 10)

            assertEquals(
                listOf(
                    BitbucketUser(
                        slug = "petrov.iv",
                        name = "petrov.iv",
                        displayName = "Петров Игорь Вадимович",
                        email = "petrov.iv@nspk.ru",
                        active = true,
                    ),
                ),
                users,
            )
            val request = server.takeRequest()
            assertEquals("/rest/api/1.0/users?filter=petrov&limit=10", request.url.encodedPath + "?" + request.url.encodedQuery)
            assertEquals(Credentials.basic("petrov.iv", "secret-password"), request.headers["Authorization"])
        }
    }

    @Test
    fun `rate limited user search maps to rate limited`() {
        assertHttpCodeMapsTo(429, ExitCode.RATE_LIMITED)
    }

    @Test
    fun `unsupported user search client error maps to unsupported client error`() {
        assertHttpCodeMapsTo(400, ExitCode.UNSUPPORTED_CLIENT_ERROR)
    }

    @Test
    fun `malformed user search response maps to malformed response`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/json")
                    .body("{broken")
                    .build(),
            )

            val error = assertFailsWith<VpException> {
                users(server).search("petrov")
            }

            assertEquals(ExitCode.MALFORMED_RESPONSE, error.exitCode)
        }
    }

    private fun assertHttpCodeMapsTo(code: Int, expected: ExitCode) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(code)
                    .setHeader("Content-Type", "application/json")
                    .body("""{"message":"error"}""")
                    .build(),
            )

            val error = assertFailsWith<VpException> {
                users(server).search("petrov")
            }

            assertEquals(expected, error.exitCode)
            assertEquals(1, server.requestCount)
        }
    }

    private fun users(server: MockWebServer): BitbucketUsers =
        BitbucketUsers(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            auth = Auth(method = "basic", username = "petrov.iv", password = "secret-password"),
        )
}
