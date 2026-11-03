package com.juying.tv.util

import java.security.MessageDigest
import java.util.UUID

fun stableId(vararg parts: String): String =
    UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray(Charsets.UTF_8)).toString()

fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
