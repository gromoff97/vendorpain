package ru.vp.bitbucket

data class BitbucketUser(
    val slug: String,
    val name: String,
    val displayName: String,
    val email: String?,
    val active: Boolean,
)
