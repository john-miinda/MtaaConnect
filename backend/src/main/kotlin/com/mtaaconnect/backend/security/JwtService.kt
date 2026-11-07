package com.mtaaconnect.backend.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import com.mtaaconnect.backend.model.UserRole
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date

@Service
class JwtService(
    @Value("\${jwt.secret}") jwtSecret: String,
    @Value("\${jwt.expiration}") private val expiration: Long,
) {
    private val signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))

    init {
        require(expiration > 0) { "JWT expiration must be greater than zero" }
    }

    fun generateToken(email: String, role: UserRole = UserRole.CUSTOMER): String {
        require(email.isNotBlank()) { "JWT subject must not be blank" }
        val now = Date()
        return Jwts.builder()
            .subject(email)
            .claim(ROLE_CLAIM, role.name)
            .issuedAt(now)
            .expiration(Date(Math.addExact(now.time, expiration)))
            .signWith(signingKey)
            .compact()
    }

    fun extractEmail(token: String): String {
        val subject = parseClaims(token).payload.subject
        return subject?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("JWT subject is missing")
    }

    fun extractRole(token: String): UserRole {
        val roleClaim = parseClaims(token).payload[ROLE_CLAIM] ?: return UserRole.CUSTOMER
        val role = roleClaim as? String ?: throw IllegalArgumentException("JWT role is invalid")
        return UserRole.entries.firstOrNull { it.name == role }
            ?: throw IllegalArgumentException("JWT role is invalid")
    }

    fun isTokenValid(token: String): Boolean =
        try {
            extractEmail(token)
            extractRole(token)
            true
        } catch (_: JwtException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun parseClaims(token: String) =
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .also { claims ->
                requireNotNull(claims.payload.expiration) { "JWT expiration is missing" }
            }

    private companion object {
        const val ROLE_CLAIM = "role"
    }
}
