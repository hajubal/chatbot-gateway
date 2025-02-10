package com.chatbot.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityCredentialsResponse {
  private String encryptionKey;
  private String publicKey;
  private String privateKey;
}