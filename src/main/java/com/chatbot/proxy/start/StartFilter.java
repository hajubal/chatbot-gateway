package com.chatbot.proxy.start;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class StartFilter implements GlobalFilter, Ordered {


  public StartFilter() {
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 고유한 상관관계 ID 생성
    String correlationId = UUID.randomUUID().toString();

    // 현재 시간 기록
    long startTime = System.currentTimeMillis();

    // 요청 헤더에 상관관계 ID와 시작 시간 추가
    ServerWebExchange modifiedExchange = exchange.mutate()
        .request(
            exchange.getRequest().mutate()
                .header("X-Correlation-ID", correlationId)
                .header("X-Start-Time", String.valueOf(startTime))
                .build()
        )
        .build();

    return chain.filter(modifiedExchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}

