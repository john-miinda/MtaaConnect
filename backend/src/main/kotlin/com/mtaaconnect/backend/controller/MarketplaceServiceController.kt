package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.ServiceRequest
import com.mtaaconnect.backend.dto.ServiceResponse
import com.mtaaconnect.backend.service.MarketplaceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MarketplaceServiceController(
    private val marketplaceService: MarketplaceService,
) {
    @PostMapping("/services")
    fun createService(
        authentication: Authentication,
        @Valid @RequestBody request: ServiceRequest,
    ): ResponseEntity<ServiceResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(marketplaceService.create(authentication.name, request))

    @GetMapping("/services")
    fun browseServices(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) location: String?,
    ): List<ServiceResponse> = marketplaceService.browse(category, location)

    @GetMapping("/services/{id}")
    fun getService(@PathVariable id: String): ServiceResponse =
        marketplaceService.getById(id)

    @GetMapping("/providers/{providerId}/services")
    fun getProviderServices(@PathVariable providerId: String): List<ServiceResponse> =
        marketplaceService.getAvailableByProvider(providerId)

    @PutMapping("/services/{id}")
    fun updateService(
        authentication: Authentication,
        @PathVariable id: String,
        @Valid @RequestBody request: ServiceRequest,
    ): ServiceResponse = marketplaceService.update(authentication.name, id, request)

    @DeleteMapping("/services/{id}")
    fun deleteService(authentication: Authentication, @PathVariable id: String): ResponseEntity<Void> {
        marketplaceService.delete(authentication.name, id)
        return ResponseEntity.noContent().build()
    }
}
