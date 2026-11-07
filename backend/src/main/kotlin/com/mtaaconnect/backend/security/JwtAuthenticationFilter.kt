package com.mtaaconnect.backend.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authorizationHeader.substring(BEARER_PREFIX.length).trim()
        try {
            val email = jwtService.extractEmail(token)
            val role = jwtService.extractRole(token)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                    email,
                    null,
                    listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_${role.name}")),
                )
        } catch (_: JwtException) {
            SecurityContextHolder.clearContext()
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, INVALID_TOKEN_MESSAGE)
            return
        } catch (_: IllegalArgumentException) {
            SecurityContextHolder.clearContext()
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, INVALID_TOKEN_MESSAGE)
            return
        }

        filterChain.doFilter(request, response)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val INVALID_TOKEN_MESSAGE = "Invalid or expired token"
    }
}
