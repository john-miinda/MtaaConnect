package com.mtaaconnect.backend.exception

import com.mtaaconnect.backend.service.EmailAlreadyRegisteredException
import com.mtaaconnect.backend.service.PhoneNumberAlreadyRegisteredException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalHandlerException {

    @ExceptionHandler(
        EmailAlreadyRegisteredException::class,
        PhoneNumberAlreadyRegisteredException::class,
    )
    fun handleConflict(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to (exception.message ?: "Resource already exists")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest()
            .body(mapOf("error" to (exception.message ?: "Invalid request")))

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(exception: Exception): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "An unexpected error occurred"))
}
