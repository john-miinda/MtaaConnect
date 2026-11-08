package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.ProfileImageResponse
import com.mtaaconnect.backend.model.ProfileImageCategory
import com.mtaaconnect.backend.service.ProfileImageService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.concurrent.TimeUnit

@RestController
class ProfileImageController(
    private val profileImageService: ProfileImageService,
) {
    @PostMapping("/api/profile/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        authentication: Authentication,
        @RequestParam category: ProfileImageCategory,
        @RequestParam(required = false) serviceId: String?,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ProfileImageResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(profileImageService.upload(authentication.name, category, serviceId, file))

    @GetMapping("/api/profile/images")
    fun getMyImages(authentication: Authentication): List<ProfileImageResponse> =
        profileImageService.getOwnedImages(authentication.name)

    @DeleteMapping("/api/profile/images/{id}")
    fun delete(authentication: Authentication, @PathVariable id: String): ResponseEntity<Void> {
        profileImageService.delete(authentication.name, id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/providers/{providerId}/images")
    fun getProviderImages(@PathVariable providerId: String): List<ProfileImageResponse> =
        profileImageService.getProviderImages(providerId)

    @GetMapping("/api/services/{serviceId}/images")
    fun getServiceImages(@PathVariable serviceId: String): List<ProfileImageResponse> =
        profileImageService.getServiceImages(serviceId)

    @GetMapping("/api/media/{id}")
    fun getImage(
        @PathVariable id: String,
        authentication: Authentication?,
    ): ResponseEntity<ByteArray> {
        val image = profileImageService.getImage(id, authentication?.name)
        val cacheControl = if (image.isPublic) {
            CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic()
        } else {
            CacheControl.noStore().cachePrivate()
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType))
            .cacheControl(cacheControl)
            .header("X-Content-Type-Options", "nosniff")
            .body(image.content)
    }
}
