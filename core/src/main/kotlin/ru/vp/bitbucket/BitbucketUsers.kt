package ru.vp.bitbucket

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.vp.config.Auth
import ru.vp.config.Ssh
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.net.HttpAccess
import ru.vp.net.NetworkErrors
import java.io.IOException

class BitbucketUsers(
    private val baseUrl: String,
    private val auth: Auth,
    timeoutSeconds: Int = 30,
    insecure: Boolean = false,
    ssh: Ssh? = null,
    private val access: HttpAccess = HttpAccess.open(ssh, timeoutSeconds, insecure),
    private val http: OkHttpClient = access.client,
) : AutoCloseable {
    override fun close() {
        access.close()
    }

    fun search(query: String, limit: Int = 10): List<BitbucketUser> {
        if (query.isBlank()) return emptyList()

        val req = try {
            Request.Builder()
                .url(url(query, limit))
                .header("Authorization", auth.authorizationHeader())
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            throw VpException(ExitCode.INVALID_URL, "invalid Bitbucket URL: $baseUrl", e)
        }

        return try {
            http.newCall(req).execute().use { res ->
                val body = res.body.string()
                when (res.code) {
                    200 -> parse(body)
                    401 -> throw VpException(ExitCode.AUTHENTICATION_FAILED, "authentication failed while searching Bitbucket users")
                    403 -> throw VpException(ExitCode.PERMISSION_DENIED, "permission denied while searching Bitbucket users")
                    404 -> throw VpException(ExitCode.ENDPOINT_NOT_FOUND, "Bitbucket user search endpoint not found")
                    429 -> throw VpException(ExitCode.RATE_LIMITED, "Bitbucket rate limited user search")
                    in 400..499 -> throw VpException(ExitCode.UNSUPPORTED_CLIENT_ERROR, "Bitbucket user search returned unsupported HTTP ${res.code}")
                    else -> throw VpException(ExitCode.INTERNAL_ERROR, "Bitbucket user search returned HTTP ${res.code}")
                }
            }
        } catch (e: IOException) {
            throw VpException(NetworkErrors.classify(e), "network error while searching Bitbucket users", e)
        }
    }

    private fun parse(body: String): List<BitbucketUser> =
        try {
            mapper.readValue<Page>(body).values.map(User::toDomain)
        } catch (e: JsonProcessingException) {
            throw VpException(ExitCode.MALFORMED_RESPONSE, "Bitbucket user search returned malformed JSON", e)
        }

    private fun url(query: String, limit: Int) = baseUrl
        .trimEnd('/')
        .toHttpUrl()
        .newBuilder()
        .addPathSegments("rest/api/1.0/users")
        .addQueryParameter("filter", query)
        .addQueryParameter("limit", limit.toString())
        .build()

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Page(
        val values: List<User> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class User(
        val slug: String = "",
        val name: String = "",
        val displayName: String = "",
        val emailAddress: String? = null,
        val active: Boolean = false,
    ) {
        fun toDomain(): BitbucketUser =
            BitbucketUser(
                slug = slug,
                name = name,
                displayName = displayName,
                email = emailAddress,
                active = active,
            )
    }

    private companion object {
        private val mapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
