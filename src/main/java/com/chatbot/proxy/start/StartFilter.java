package com.chatbot.proxy.start;

import com.chatbot.proxy.util.TimeUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class StartFilter implements GlobalFilter, Ordered {

  private final MeterRegistry meterRegistry;

  public StartFilter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
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

    return chain.filter(modifiedExchange)
        .doOnSuccess(v -> {
          // Response Header에 값 추가
          String endCorrelationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
          String endTimeStr = exchange.getResponse().getHeaders().getFirst("X-End-Time");

          if (endCorrelationId != null && endTimeStr != null) {
            long endTime = Long.parseLong(endTimeStr);
            long totalProcessingTime = System.currentTimeMillis() - endTime;

            log.info("Response process time: {} ms", totalProcessingTime);

            // 메트릭 기록
            Timer.builder("gateway.response.proxy.time")
    //          .tag("correlation_id", correlationId)
                .register(meterRegistry)
                .record(totalProcessingTime, TimeUnit.MILLISECONDS);
          }
    });
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}

