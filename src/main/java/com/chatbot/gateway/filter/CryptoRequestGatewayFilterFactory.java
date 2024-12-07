package com.chatbot.gateway.filter;

import com.chatbot.gateway.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * streamlit -> gateway -> ollama
 *
 * streamlit 에서 암호화되어 올라온 메시지를 복호화 해서 ollama에 전달함
 */
@Slf4j
@Component
public class CryptoRequestGatewayFilterFactory extends AbstractGatewayFilterFactory<CryptoRequestGatewayFilterFactory.Config>
        implements Ordered {

  private final CryptoUtil cryptoUtil;

  @Value("${app.message.encrypted:false}")
  private boolean isEncrypt;

  public CryptoRequestGatewayFilterFactory() {
    super(Config.class);

    this.cryptoUtil = new CryptoUtil("my_very_secret_key_32_bytes_long");
  }

  @Override
  public GatewayFilter apply(Config config) {

    log.info("Init CryptoRequestGatewayFilterFactory.");

    return (exchange, chain) -> {
      log.info("Call CryptoRequestGatewayFilter.");

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
                    log.debug("Original request body: {}", originalBody);

                    String decryptedBody = null;

                    if(isEncrypt) {
                      // Request Body 암호화
                      try {
                        decryptedBody = cryptoUtil.decrypt(originalBody);
                      } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                      }
                      log.debug("Decrypted request body: {}", decryptedBody);
                    } else {
                      decryptedBody = originalBody;
                    }

                    // 새로운 Request Body 작성
                    byte[] newBodyBytes = decryptedBody.getBytes(StandardCharsets.UTF_8);
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

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  public static class Config {
    // 설정 추가가 필요하다면 여기에 정의
  }
}