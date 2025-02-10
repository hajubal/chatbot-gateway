package com.chatbot.gateway.service;

import com.chatbot.gateway.dto.SecurityCredentialsResponse;
import com.chatbot.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityCredentialsServiceTest {

  @Mock
  private JwtUtil jwtUtil;

  private SecurityCredentialsService securityCredentialsService;

  @BeforeEach
  void setUp() {
    securityCredentialsService = new SecurityCredentialsService(jwtUtil);
  }

  @Test
  void shouldGenerateCredentialsWithValidToken() {
    // Given
    String validToken = "valid.jwt.token";
    String authHeader = "Bearer " + validToken;
    when(jwtUtil.validateToken(validToken)).thenReturn(true);

    // When
    SecurityCredentialsResponse response = securityCredentialsService.generateCredentials(authHeader);

    // Then
    assertNotNull(response);
    assertNotNull(response.getEncryptionKey());
    assertNotNull(response.getPublicKey());
    assertNotNull(response.getPrivateKey());
    verify(jwtUtil).validateToken(validToken);
  }

  @Test
  void shouldThrowExceptionWithInvalidToken() {
    // Given
    String invalidToken = "invalid.jwt.token";
    String authHeader = "Bearer " + invalidToken;
    when(jwtUtil.validateToken(invalidToken)).thenReturn(false);

    // When & Then
    assertThrows(IllegalArgumentException.class,
        () -> securityCredentialsService.generateCredentials(authHeader));
    verify(jwtUtil).validateToken(invalidToken);
  }

  @Test
  void shouldThrowExceptionWithInvalidAuthHeader() {
    // Given
    String invalidAuthHeader = "InvalidHeader";

    // When & Then
    assertThrows(IllegalArgumentException.class,
        () -> securityCredentialsService.generateCredentials(invalidAuthHeader));
    verifyNoInteractions(jwtUtil);
  }
}