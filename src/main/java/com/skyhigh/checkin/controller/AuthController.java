package com.skyhigh.checkin.controller;

import com.skyhigh.checkin.dto.request.LoginRequest;
import com.skyhigh.checkin.dto.request.RefreshTokenRequest;
import com.skyhigh.checkin.dto.response.LoginResponse;
import com.skyhigh.checkin.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and token management APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate passenger",
               description = "Authenticate using booking reference, last name, and email")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for booking: {}", request.getBookingReference());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
               description = "Get a new access token using refresh token")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        LoginResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate the current session")
    public ResponseEntity<Void> logout() {
        // Stateless JWT - client should discard tokens
        log.info("Logout request");
        return ResponseEntity.noContent().build();
    }
}

