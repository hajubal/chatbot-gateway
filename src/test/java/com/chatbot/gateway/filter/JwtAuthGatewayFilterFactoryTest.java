package com.chatbot.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;

import com.chatbot.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class JwtAuthGatewayFilterFactoryTest {

  private JwtUtil jwtUtil;
  private JwtAuthGatewayFilterFactory filterFactory;
  private GatewayFilter filter;

  @BeforeEach
  void setUp() {
    jwtUtil = mock(JwtUtil.class);
    filterFactory = new JwtAuthGatewayFilterFactory(jwtUtil);
    filter = filterFactory.apply(new JwtAuthGatewayFilterFactory.Config());
  }

  @Test
  void shouldAcceptValidToken() {
    // Given
    String token = "valid.jwt.token";
    when(jwtUtil.validateToken(token)).thenReturn(true);
    when(jwtUtil.getSubjectFromToken(token)).thenReturn("user123");

    ServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build()
    );

    // When
    Mono<Void> result = filter.filter(exchange, (serverWebExchange -> Mono.empty()));

    // Then
    StepVerifier.create(result)
        .verifyComplete();

    verify(jwtUtil).validateToken(token);
    verify(jwtUtil).getSubjectFromToken(token);
  }

  @Test
  void shouldRejectInvalidToken() {
    // Given
    String token = "invalid.jwt.token";
    when(jwtUtil.validateToken(token)).thenReturn(false);

    ServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build()
    );

    // When
    Mono<Void> result = filter.filter(exchange, (serverWebExchange -> Mono.empty()));

    // Then
    StepVerifier.create(result)
        .verifyComplete();

    verify(jwtUtil).validateToken(token);
    assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
  }

  @Test
  void shouldRejectMissingToken() {
    // Given
    ServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/test").build()
    );

    // When
    Mono<Void> result = filter.filter(exchange, (serverWebExchange -> Mono.empty()));

    // Then
    StepVerifier.create(result)
        .verifyComplete();

    verifyNoInteractions(jwtUtil);
    assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
  }
}