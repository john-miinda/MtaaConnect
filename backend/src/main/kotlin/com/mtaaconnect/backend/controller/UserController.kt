
package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.RegisterRequest
import com.mtaaconnect.backend.service.UserService
import com.mtaaconnect.backend.service.RegisteredUserResponse
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
        @RequestBody request: RegisterRequest
    ): ResponseEntity<RegisteredUserResponse> {

        val registeredUser = userService.register(
            fullName = request.fullName,
            email = request.email,
            password = request.password,
            phoneNumber = request.phoneNumber
        )

        return ResponseEntity.ok(registeredUser)
    }
}
