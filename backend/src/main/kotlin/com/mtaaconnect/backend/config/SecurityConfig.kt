package com.mtaaconnect.backend.config

import com.mtaaconnect.backend.security.JwtAuthenticationFilter
import com.mtaaconnect.backend.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtService: JwtService,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/users/register", "/api/auth/login").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/providers/profile",
                        "/api/providers/profile/services",
                    )
                    .hasRole("PROVIDER")
                    .requestMatchers(HttpMethod.GET, "/api/providers/**", "/api/services/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/providers/profile", "/api/services")
                    .hasRole("PROVIDER")
                    .requestMatchers(HttpMethod.PUT, "/api/providers/profile", "/api/services/**")
                    .hasRole("PROVIDER")
                    .requestMatchers(HttpMethod.DELETE, "/api/services/**")
                    .hasRole("PROVIDER")
                    .requestMatchers(HttpMethod.POST, "/api/bookings")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/bookings/my-bookings")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/bookings/provider")
                    .hasRole("PROVIDER")
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/cancel")
                    .hasRole("CUSTOMER")
                    .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/bookings/*/accept",
                        "/api/bookings/*/reject",
                        "/api/bookings/*/complete",
                    )
                    .hasRole("PROVIDER")
                    .requestMatchers(HttpMethod.GET, "/api/bookings/*")
                    .authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            allowCredentials = false
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
