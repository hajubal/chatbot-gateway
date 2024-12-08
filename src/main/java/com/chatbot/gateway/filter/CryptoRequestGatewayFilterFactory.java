package com.chatbot.gateway.filter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class CryptoRequestGatewayFilterFactory extends AbstractGatewayFilterFactory<CryptoRequestGatewayFilterFactory.Config> {

  public CryptoRequestGatewayFilterFactory() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      // 요청 본문 변형 로직
        return exchange.getRequest().getBody()
                .collectList() // Body 데이터를 DataBuffer 리스트로 수집
                .flatMap(dataBuffers -> {
                    // Body 내용을 문자열로 변환
                    StringBuilder bodyBuilder = new StringBuilder();
                    dataBuffers.forEach(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        bodyBuilder.append(new String(bytes, StandardCharsets.UTF_8));
                    });

                    String originalBody = bodyBuilder.toString();
                    log.info("Original Body: {}", originalBody);

                    // Request Body 암호화 (예: 단순 Base64 암호화)
                    String encryptedBody = Base64.getEncoder().encodeToString(originalBody.getBytes(StandardCharsets.UTF_8));
                    log.info("Encrypted Body: {}", encryptedBody);

                    // 새로운 Request Body 작성
                    byte[] newBodyBytes = encryptedBody.getBytes(StandardCharsets.UTF_8);
                    DataBuffer newBodyDataBuffer = exchange.getResponse()
                            .bufferFactory()
                            .wrap(newBodyBytes);

                    // 새로운 Request 생성
                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header("Content-Length", String.valueOf(newBodyBytes.length)) // Content-Length 재설정
                            .build();

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(new ServerHttpRequestDecorator(mutatedRequest) {
                                @Override
                                public Flux<DataBuffer> getBody() {
                                    return Flux.just(newBodyDataBuffer);
                                }
                            })
                            .build();

                    // 다음 필터 체인으로 전달
                    return chain.filter(mutatedExchange);
                });
    };
  }
  public static class Config {
    // 설정 추가가 필요하다면 여기에 정의
  }
}