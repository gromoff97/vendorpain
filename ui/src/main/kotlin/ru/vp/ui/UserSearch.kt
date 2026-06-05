package ru.vp.ui

import ru.vp.bitbucket.BitbucketUser
import ru.vp.bitbucket.BitbucketUsers

interface UserSearch : AutoCloseable {
    fun search(query: String): List<BitbucketUser>
}

class BitbucketUserSearch(
    private val form: ExportForm,
) : UserSearch {
    private val users = BitbucketUsers(
        baseUrl = form.bitbucketBaseUrl,
        auth = form.auth(),
        timeoutSeconds = form.timeoutSeconds.toIntOrNull() ?: 30,
        insecure = form.insecure,
        ssh = form.ssh(),
    )

    override fun search(query: String): List<BitbucketUser> = users.search(query)

    override fun close() {
        users.close()
    }
}
