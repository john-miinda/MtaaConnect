package com.mtaaconnect.android.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Base64

object SessionDecoder {
    fun decode(token: String, fullName: String): Session {
        val parts = token.split('.')
        require(parts.size == 3) { "The server returned an invalid session token." }
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payload = Gson().fromJson(payloadBytes.toString(Charsets.UTF_8), JsonObject::class.java)
        val email = payload.get("sub")?.asString?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("The session token has no user identity.")
        val expiry = payload.get("exp")?.asLong
            ?: throw IllegalArgumentException("The session token has no expiration.")
        require(expiry > System.currentTimeMillis() / 1000) { "The session has expired. Please sign in again." }
        val role = payload.get("role")?.asString?.uppercase() ?: "CUSTOMER"
        require(role in setOf("CUSTOMER", "PROVIDER", "ADMIN")) { "The session contains an unsupported account role." }
        return Session(token, email, fullName, role, expiry)
    }
}
