package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.ProfileImage
import org.springframework.data.mongodb.repository.MongoRepository

interface ProfileImageRepository : MongoRepository<ProfileImage, String> {
    fun findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId: String): List<ProfileImage>

    fun findByProviderProfileIdAndServiceIdIsNullOrderByCreatedAtDesc(
        providerProfileId: String,
    ): List<ProfileImage>

    fun findByServiceIdOrderByCreatedAtDesc(serviceId: String): List<ProfileImage>

    fun findByIdAndOwnerUserId(id: String, ownerUserId: String): ProfileImage?
}
