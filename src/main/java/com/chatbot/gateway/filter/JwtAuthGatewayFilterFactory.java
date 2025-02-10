package com.chatbot.gateway.filter;

import com.chatbot.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

  private final JwtUtil jwtUtil;

  public JwtAuthGatewayFilterFactory(JwtUtil jwtUtil) {
    super(Config.class);
    this.jwtUtil = jwtUtil;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      String token = extractToken(exchange);

      if (token == null) {
        return onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED);
      }

      if (!jwtUtil.validateToken(token)) {
        return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
      }

      // Add the validated subject to the request headers
      String subject = jwtUtil.getSubjectFromToken(token);
      exchange.getRequest().mutate()
          .header("X-Auth-Subject", subject)
          .build();

      return chain.filter(exchange);
    };
  }

  private String extractToken(ServerWebExchange exchange) {
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }
    return null;
  }

  private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
    log.error(message);
    exchange.getResponse().setStatusCode(status);
    return exchange.getResponse().setComplete();
  }

  public static class Config {
    // Configuration properties if needed
  }
}
