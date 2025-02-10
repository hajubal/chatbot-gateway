package com.chatbot.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

@Configuration
public class SecurityCredentialsConfig {

  @Bean
  public StringKeyGenerator stringKeyGenerator() {
    return KeyGenerators.string();
  }
}