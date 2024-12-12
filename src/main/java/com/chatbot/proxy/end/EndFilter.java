package com.chatbot.proxy.end;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class EndFilter implements GlobalFilter, Ordered {

  private final MeterRegistry meterRegistry;

  public EndFilter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 상관관계 ID와 시작 시간 추출
    String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    String startTimeStr = exchange.getRequest().getHeaders().getFirst("X-Start-Time");

    if (correlationId != null && startTimeStr != null) {
      long startTime = Long.parseLong(startTimeStr);
      long endTime = System.currentTimeMillis();
      long totalProcessingTime = endTime - startTime;

      // 메트릭 기록
      Timer.builder("gateway.processing.time")
//          .tag("correlation_id", correlationId)
          .register(meterRegistry)
          .record(totalProcessingTime, TimeUnit.MILLISECONDS);
    }

    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}

