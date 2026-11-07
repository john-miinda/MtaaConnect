
package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.RegisterRequest
import com.mtaaconnect.backend.dto.UserResponse
import com.mtaaconnect.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<UserResponse> {

        val registeredUser = userService.register(
            fullName = request.fullName,
            email = request.email,
            password = request.password,
            phoneNumber = request.phoneNumber,
            role = request.role,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser)
    }
}
