package com.chatbot.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class MetricsFilter implements GlobalFilter, Ordered {

  private final MeterRegistry meterRegistry;

  public MetricsFilter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Timer.Sample sample = Timer.start(meterRegistry);

    return chain.filter(exchange)
        .doFinally(signalType -> {
          // 요청 처리 시간 메트릭
          sample.stop(
              Timer.builder("gateway.global.processing.time")
                  .tag("path", exchange.getRequest().getPath().toString())
                  .tag("method", exchange.getRequest().getMethod().name())
                  .tag("status", String.valueOf(exchange.getResponse().getStatusCode().value()))
                  .register(meterRegistry)
          );
        });
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}

