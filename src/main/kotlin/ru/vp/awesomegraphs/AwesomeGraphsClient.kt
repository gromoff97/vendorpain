package ru.vp.awesomegraphs

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.vp.config.OptionsConfig
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class AwesomeGraphsClient(
    private val options: OptionsConfig,
    private val httpClient: OkHttpClient = defaultHttpClient(options.timeoutSeconds),
    private val csvResponseValidator: CsvResponseValidator = CsvResponseValidator(),
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
) : CsvDownloader {
    override fun downloadCsv(slug: String): CsvDownloadResult {
        val request = Request.Builder()
            .url(buildUrl(slug))
            .header("Authorization", "Bearer ${options.token}")
            .get()
            .build()

        var attempt = 0
        while (true) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val contentType = response.header("Content-Type")
                    val bytes = response.body.bytes()

                    if (statusCode in 500..599) {
                        if (attempt < options.retries) {
                            sleepMillis(backoffMillis(attempt))
                            attempt += 1
                            continue
                        }
                        throw VpException(
                            ExitCode.SERVER_ERROR,
                            "Awesome Graphs returned HTTP $statusCode for user '$slug' after ${attempt + 1} attempts",
                        )
                    }

                    handleHttpError(statusCode, slug, contentType, bytes)
                    csvResponseValidator.validate(contentType, bytes)

                    return CsvDownloadResult(
                        bytes = bytes,
                        requestUrl = request.url.toString(),
                        statusCode = statusCode,
                        contentType = contentType,
                        attempts = attempt + 1,
                    )
                }
            } catch (e: IOException) {
                if (attempt < options.retries) {
                    sleepMillis(backoffMillis(attempt))
                    attempt += 1
                    continue
                }
                throw VpException(
                    ExitCode.NETWORK_ERROR,
                    "network error while downloading CSV for user '$slug' after ${attempt + 1} attempts",
                    e,
                )
            }
        }
    }

    private fun buildUrl(slug: String) = options.baseUrl
        .trimEnd('/')
        .toHttpUrl()
        .newBuilder()
        .addPathSegment("users")
        .addPathSegment(slug)
        .addPathSegment("commits")
        .addPathSegment("export")
        .addPathSegment("csv")
        .addQueryParameter("sinceDate", options.sinceDate)
        .addQueryParameter("untilDate", options.untilDate)
        .addQueryParameter("merges", options.merges)
        .addQueryParameter("order", options.order)
        .build()

    private fun handleHttpError(statusCode: Int, slug: String, contentType: String?, bytes: ByteArray) {
        when (statusCode) {
            200 -> return
            401 -> throw VpException(ExitCode.AUTHENTICATION_FAILED, "authentication failed while downloading CSV")
            403 -> throw VpException(ExitCode.PERMISSION_DENIED, "permission denied while downloading CSV for user '$slug'")
            404 -> {
                val exitCode = if (csvResponseValidator.looksLikeHtml(contentType, bytes)) {
                    ExitCode.ENDPOINT_NOT_FOUND
                } else {
                    ExitCode.USER_NOT_FOUND
                }
                throw VpException(exitCode, "Awesome Graphs returned HTTP 404 for user '$slug'")
            }
            in 400..499 -> throw VpException(
                ExitCode.INTERNAL_ERROR,
                "Awesome Graphs returned unsupported HTTP $statusCode for user '$slug'",
            )
            else -> throw VpException(
                ExitCode.INTERNAL_ERROR,
                "Awesome Graphs returned unexpected HTTP $statusCode for user '$slug'",
            )
        }
    }

    private fun backoffMillis(attempt: Int): Long = 1_000L * (1L shl attempt.coerceAtMost(20))

    private companion object {
        private fun defaultHttpClient(timeoutSeconds: Int): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
    }
}
