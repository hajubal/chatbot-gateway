package com.chatbot.gateway.controller;

import com.chatbot.gateway.dto.SecurityCredentialsResponse;
import com.chatbot.gateway.service.SecurityCredentialsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SecurityCredentialsController {

  private final SecurityCredentialsService securityCredentialsService;

  @GetMapping("/security-credentials")
  public ResponseEntity<SecurityCredentialsResponse> getSecurityCredentials(
      @RequestHeader("Authorization") String authHeader) {

    log.info("Received security credentials request");
    SecurityCredentialsResponse credentials = securityCredentialsService.generateCredentials(authHeader);
    return ResponseEntity.ok(credentials);
  }
}