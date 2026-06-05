package ru.vp.bitbucket

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import ru.vp.config.Auth
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
