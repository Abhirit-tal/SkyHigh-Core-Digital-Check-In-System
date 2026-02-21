package com.skyhigh.checkin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.checkin.dto.request.LoginRequest;
import com.skyhigh.checkin.dto.response.LoginResponse;
import com.skyhigh.checkin.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .bookingReference("ABC123")
                .lastName("Doe")
                .email("john.doe@email.com")
                .build();

        LoginResponse response = LoginResponse.builder()
                .accessToken("test-token")
                .refreshToken("test-refresh-token")
                .tokenType("Bearer")
                .expiresIn(7200)
                .passenger(LoginResponse.PassengerInfo.builder()
                        .id(UUID.randomUUID())
                        .firstName("John")
                        .lastName("Doe")
                        .email("john.doe@email.com")
                        .build())
                .flights(List.of())
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_ShouldReturn400_WhenBookingReferenceIsInvalid() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .bookingReference("INVALID!")  // Invalid format
                .lastName("Doe")
                .email("john.doe@email.com")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_ShouldReturn400_WhenEmailIsInvalid() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .bookingReference("ABC123")
                .lastName("Doe")
                .email("invalid-email")  // Invalid format
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

