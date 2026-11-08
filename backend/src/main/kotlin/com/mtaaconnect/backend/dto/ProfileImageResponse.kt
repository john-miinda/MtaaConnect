package com.mtaaconnect.backend.dto

import com.mtaaconnect.backend.model.ProfileImageCategory
import java.time.Instant

data class ProfileImageResponse(
    val id: String,
    val category: ProfileImageCategory,
    val url: String,
    val serviceId: String?,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: Instant,
)
