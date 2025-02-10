package com.chatbot.gateway.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
  private final SecretKey key;
  private final long tokenValidityInMilliseconds;

  public JwtUtil(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.token-validity-in-duration}") Duration tokenValidityInDuration) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.tokenValidityInMilliseconds = tokenValidityInDuration.toMillis();
  }

  public String createToken(String subject) {
    long now = System.currentTimeMillis();
    Date validity = new Date(now + tokenValidityInMilliseconds);

    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(new Date(now))
        .setExpiration(validity)
        .signWith(key)
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parserBuilder()
          .setSigningKey(key)
          .build()
          .parseClaimsJws(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.error("Invalid JWT token: {}", e.getMessage());
      return false;
    }
  }

  public String getSubjectFromToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody()
        .getSubject();
  }
}