package ru.vp.awesomegraphs

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.net.HttpAccess
import ru.vp.net.NetworkErrors
import java.io.IOException

class GraphsClient(
    private val options: Options,
    private val access: HttpAccess = HttpAccess.open(options.ssh, options.timeoutSeconds, options.insecure),
    private val httpClient: OkHttpClient = access.client,
    private val csv: CsvGuard = CsvGuard(),
    private val sleep: (Long) -> Unit = Thread::sleep,
) : CsvSource {
    override fun close() {
        access.close()
    }

    override fun downloadCsv(slug: String): CsvResult {
        val req = try {
            request(slug)
        } catch (e: IllegalArgumentException) {
            throw VpException(ExitCode.INVALID_URL, "invalid Awesome Graphs API URL: ${options.baseUrl}", e)
        }
        var tries = 1
        while (true) {
            try {
                fetch(req, slug, tries)?.let { return it }
            } catch (e: IOException) {
                if (!retry(tries)) {
                    val code = NetworkErrors.classify(e)
                    throw VpException(
                        code,
                        "network error while downloading CSV for user '$slug' after $tries attempts",
                        e,
                    )
                }
            }
            wait(tries++)
        }
    }

    private fun request(slug: String): Request = Request.Builder()
        .url(url(slug))
        .header("Authorization", options.auth.authorizationHeader())
        .get()
        .build()

    private fun fetch(req: Request, slug: String, tries: Int): CsvResult? =
        httpClient.newCall(req).execute().use { response ->
            val status = response.code
            val type = response.header("Content-Type")
            val bytes = response.body.bytes()

            if (status in 500..599) {
                return server(status, slug, tries)
            }

            handle(status, slug, type, bytes)
            csv.validate(type, bytes)

            CsvResult(
                bytes = bytes,
                url = req.url.toString(),
                status = status,
                type = type,
                tries = tries,
            )
        }

    private fun url(slug: String) = options.baseUrl
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

    private fun server(status: Int, slug: String, tries: Int): CsvResult? =
        if (retry(tries)) {
            null
        } else {
            fail(ExitCode.SERVER_ERROR, "Awesome Graphs returned HTTP $status for user '$slug' after $tries attempts")
        }

    private fun handle(status: Int, slug: String, type: String?, bytes: ByteArray) {
        when (status) {
            200 -> return
            401 -> throw VpException(ExitCode.AUTHENTICATION_FAILED, "authentication failed while downloading CSV")
            403 -> throw VpException(ExitCode.PERMISSION_DENIED, "permission denied while downloading CSV for user '$slug'")
            429 -> throw VpException(ExitCode.RATE_LIMITED, "Awesome Graphs rate limited CSV download for user '$slug'")
            404 -> {
                val code = if (csv.looksLikeHtml(type, bytes)) {
                    ExitCode.ENDPOINT_NOT_FOUND
                } else {
                    ExitCode.USER_NOT_FOUND
                }
                fail(code, "Awesome Graphs returned HTTP 404 for user '$slug'")
            }
            in 400..499 -> throw VpException(
                ExitCode.UNSUPPORTED_CLIENT_ERROR,
                "Awesome Graphs returned unsupported HTTP $status for user '$slug'",
            )
            else -> throw VpException(
                ExitCode.INTERNAL_ERROR,
                "Awesome Graphs returned unexpected HTTP $status for user '$slug'",
            )
        }
    }

    private fun retry(tries: Int): Boolean = tries <= options.retries

    private fun wait(tries: Int) = sleep(1_000L * (1L shl (tries - 1).coerceAtMost(20)))

    private fun fail(code: ExitCode, message: String): Nothing =
        throw VpException(code, message)

}
