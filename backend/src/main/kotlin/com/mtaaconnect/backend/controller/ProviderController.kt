package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.ProviderProfileRequest
import com.mtaaconnect.backend.dto.ProviderProfileResponse
import com.mtaaconnect.backend.dto.ServiceResponse
import com.mtaaconnect.backend.service.MarketplaceService
import com.mtaaconnect.backend.service.ProviderProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/providers")
class ProviderController(
    private val providerProfileService: ProviderProfileService,
    private val marketplaceService: MarketplaceService,
) {
    @PostMapping("/profile")
    fun createProfile(
        authentication: Authentication,
        @Valid @RequestBody request: ProviderProfileRequest,
    ): ResponseEntity<ProviderProfileResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(providerProfileService.create(authentication.name, request))

    @GetMapping("/{id}")
    fun getProfile(@PathVariable id: String): ProviderProfileResponse =
        providerProfileService.getById(id)

    @GetMapping("/profile")
    fun getMyProfile(authentication: Authentication): ProviderProfileResponse =
        providerProfileService.getForProvider(authentication.name)

    @GetMapping("/profile/services")
    fun getMyServices(authentication: Authentication): List<ServiceResponse> =
        marketplaceService.getManagedByProvider(authentication.name)

    @PutMapping("/profile")
    fun updateProfile(
        authentication: Authentication,
        @Valid @RequestBody request: ProviderProfileRequest,
    ): ProviderProfileResponse =
        providerProfileService.update(authentication.name, request)
}
