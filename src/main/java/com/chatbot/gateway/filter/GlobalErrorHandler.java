package com.chatbot.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GlobalErrorHandler implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain.filter(exchange)
        .onErrorResume(Exception.class, e -> handleError(exchange, e));
  }

  private Mono<Void> handleError(ServerWebExchange exchange, Throwable throwable) {
    log.error("Gateway Error Processing", throwable);

    // 에러 유형 분류 및 매핑
    HttpStatusCode status = determineHttpStatus(throwable);

    // 상세 에러 응답 생성
    Map<String, Object> errorDetails = createErrorResponse(throwable, status);

    // 응답 설정
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // 에러 응답 버퍼 생성
    byte[] errorBytes = convertErrorToBytes(errorDetails);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorBytes);

    return exchange.getResponse().writeWith(Flux.just(buffer));
  }

  // HTTP 상태 코드 결정 로직
  private HttpStatusCode determineHttpStatus(Throwable throwable) {
    if (throwable instanceof ResponseStatusException) {
      return ((ResponseStatusException) throwable).getStatusCode();
    } else if (throwable instanceof IllegalArgumentException) {
      return HttpStatus.BAD_REQUEST;
    } else if (throwable instanceof SecurityException) {
      return HttpStatus.FORBIDDEN;
    } else if (throwable instanceof RuntimeException) {
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  // 에러 응답 상세 정보 생성
  private Map<String, Object> createErrorResponse(Throwable throwable, HttpStatusCode status) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("timestamp", System.currentTimeMillis());
    errorResponse.put("status", status.value());
    errorResponse.put("message", extractErrorMessage(throwable));

    // 디버그용 추가 정보 (프로덕션에서는 민감한 정보 제외)
    if (log.isDebugEnabled()) {
      errorResponse.put("exception", throwable.getClass().getName());
    }

    return errorResponse;
  }

  // 안전한 에러 메시지 추출
  private String extractErrorMessage(Throwable throwable) {
    return throwable.getMessage() != null
        ? throwable.getMessage()
        : "An unexpected error occurred";
  }

  // 에러 응답을 바이트로 변환
  private byte[] convertErrorToBytes(Map<String, Object> errorDetails) {
    try {
      return new ObjectMapper()
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(errorDetails)
          .getBytes(StandardCharsets.UTF_8);
    } catch (JsonProcessingException e) {
      log.error("Error converting error response to JSON", e);
      return "{}".getBytes(StandardCharsets.UTF_8);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

}
