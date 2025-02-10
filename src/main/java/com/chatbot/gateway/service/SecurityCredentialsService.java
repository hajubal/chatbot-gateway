package com.chatbot.gateway.service;

import com.chatbot.gateway.dto.SecurityCredentialsResponse;
import com.chatbot.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Service;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityCredentialsService {

  private final JwtUtil jwtUtil;
  private final StringKeyGenerator keyGenerator = KeyGenerators.string();

  public SecurityCredentialsResponse generateCredentials(String authHeader) {
    // JWT 토큰 추출 및 검증
    String token = extractAndValidateToken(authHeader);

    try {
      // 암호화 키 생성
      String encryptionKey = generateEncryptionKey();

      // RSA 키 쌍 생성
      KeyPair keyPair = generateKeyPair();
      String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
      String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

      return SecurityCredentialsResponse.builder()
          .encryptionKey(encryptionKey)
          .publicKey(publicKey)
          .privateKey(privateKey)
          .build();

    } catch (Exception e) {
      log.error("Error generating security credentials", e);
      throw new RuntimeException("Failed to generate security credentials", e);
    }
  }

  private String extractAndValidateToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Invalid authorization header");
    }

    String token = authHeader.substring(7);
    if (!jwtUtil.validateToken(token)) {
      throw new IllegalArgumentException("Invalid JWT token");
    }

    return token;
  }

  private String generateEncryptionKey() {
    return keyGenerator.generateKey();
  }

  private KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }
}