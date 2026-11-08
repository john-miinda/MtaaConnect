package com.mtaaconnect.backend.exception

import com.mtaaconnect.backend.service.EmailAlreadyRegisteredException
import com.mtaaconnect.backend.service.PhoneNumberAlreadyRegisteredException
import com.mtaaconnect.backend.service.AccountAlreadyRegisteredException
import com.mtaaconnect.backend.service.MarketplaceConflictException
import com.mtaaconnect.backend.service.MarketplaceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice
class GlobalHandlerException {

    @ExceptionHandler(
        EmailAlreadyRegisteredException::class,
        PhoneNumberAlreadyRegisteredException::class,
        AccountAlreadyRegisteredException::class,
        MarketplaceConflictException::class,
    )
    fun handleConflict(exception: RuntimeException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to (exception.message ?: "Resource already exists")))

    @ExceptionHandler(MarketplaceNotFoundException::class)
    fun handleNotFound(exception: MarketplaceNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (exception.message ?: "Resource not found")))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(mapOf("error" to "Access denied"))

    @ExceptionHandler(
        IllegalArgumentException::class,
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
    )
    fun handleBadRequest(exception: Exception): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest()
            .body(
                mapOf(
                    "error" to when (exception) {
                        is MethodArgumentNotValidException ->
                            exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Invalid request"
                        is HttpMessageNotReadableException -> "Invalid request body"
                        else -> exception.message ?: "Invalid request"
                    },
                ),
            )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(mapOf("error" to "Images must be 5 MB or smaller"))

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(@Suppress("UNUSED_PARAMETER") exception: Exception): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "An unexpected error occurred"))
}
