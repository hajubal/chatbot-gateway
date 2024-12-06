package com.chatbot.gateway.filter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class CryptoRequestGatewayFilterFactory extends AbstractGatewayFilterFactory<CryptoRequestGatewayFilterFactory.Config> {

  public CryptoRequestGatewayFilterFactory() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      // 요청 본문 변형 로직
      return DataBufferUtils.join(exchange.getRequest().getBody())
          .flatMap(dataBuffer -> {
            // 원본 요청 본문을 문자열로 변환
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);

            // 요청 본문 변형
            String originalBody = new String(bytes, StandardCharsets.UTF_8);
            String modifiedBody = modifyRequestBody(originalBody);

            // 변형된 본문을 위한 새로운 DataBuffer 생성
            byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
            DataBuffer modifiedDataBuffer = exchange.getRequest().bufferFactory().wrap(modifiedBytes);

            // 새로운 요청 생성 (ServerHttpRequestDecorator 사용)
            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
              @Override
              public Flux<DataBuffer> getBody() {
                return Flux.just(modifiedDataBuffer);
              }

              @Override
              public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.addAll(exchange.getRequest().getHeaders());
                headers.set("X-Body-Modified", "true");
                return headers;
              }
            };

            // 변형된 요청으로 exchange 생성
            ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

            // 체인 필터 진행
            return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
              // 응답 후처리 로직 (옵션)
              System.out.println("Request processed with body modification");
            }));
          })
          .onErrorResume(ex -> {
            // 에러 처리
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
          });
    };
  }

  protected DataBuffer wrap(ByteBuf byteBuf, ServerHttpResponse response) {
    DataBufferFactory bufferFactory = response.bufferFactory();
    if (bufferFactory instanceof NettyDataBufferFactory) {
      NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
      return factory.wrap(byteBuf);
    }
    // MockServerHttpResponse creates these
    else if (bufferFactory instanceof DefaultDataBufferFactory) {
      DataBuffer buffer = ((DefaultDataBufferFactory) bufferFactory).allocateBuffer(byteBuf.readableBytes());
      buffer.write(byteBuf.nioBuffer());
      byteBuf.release();
      return buffer;
    }
    throw new IllegalArgumentException("Unkown DataBufferFactory type " + bufferFactory.getClass());
  }

  // 요청 본문 변형 메서드 (비즈니스 로직에 따라 구현)
  private String modifyRequestBody(String originalBody) {
    // 예시: 간단한 본문 변형 로직
    return originalBody + " [Modified by Gateway]";
  }

  // 필터 설정을 위한 내부 설정 클래스
  public static class Config {
    private boolean enabled;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  @Override
  public List<String> shortcutFieldOrder() {
    return Arrays.asList("enabled");
  }
}