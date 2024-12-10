package com.chatbot.gateway.filter;

import com.chatbot.gateway.dto.MessageDto;
import com.chatbot.gateway.util.CryptoUtil;
import com.chatbot.gateway.util.SignUtil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import reactor.core.publisher.Mono;

/**
 * streamlit -> gateway -> ollama
 *
 * streamlit 에서 암호화되어 올라온 메시지를 복호화 해서 ollama에 전달함
 */
@Slf4j
@Component
public class CryptoRequestGatewayFilterFactory extends AbstractGatewayFilterFactory<CryptoRequestGatewayFilterFactory.Config>
        implements Ordered {

  Logger timeLog = LoggerFactory.getLogger("time");

  private final CryptoUtil cryptoUtil;

  private final SignUtil signUtil;

  public CryptoRequestGatewayFilterFactory(@Value("${app.message.enc_key}") String encKey
      , @Value("${app.message.private_key}") String privateKey
      , @Value("${app.message.public_key}") String publicKey) {
    super(Config.class);

    this.cryptoUtil = new CryptoUtil(encKey);
    this.signUtil = new SignUtil(privateKey, publicKey);
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
                  MessageDto messageDto = getBody(dataBuffers);

                  log.debug("Original request: {}", messageDto);

                  //message 처리(서명 검증, 복호화)
                  String requestBody = messageHandle(messageDto);

                  // 새로운 Request Body 작성
                  ServerWebExchange mutatedExchange = getServerWebExchange(exchange, requestBody);

                  // 다음 필터 체인으로 전달
                  return chain.filter(mutatedExchange);
                });
    };
  }

  private static ServerWebExchange getServerWebExchange(ServerWebExchange exchange,
      String requestBody) {
    byte[] newBodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
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
    return mutatedExchange;
  }

  private String messageHandle(MessageDto messageDto) {
    String requestBody = "";

    long start = System.nanoTime();

    if(messageDto.isSigned()) {
      try {
        boolean verified = signUtil.verify(messageDto.getMessage(), messageDto.getSignature());

        log.debug("Signature verified: {}", verified);

        if(!verified) throw new IllegalArgumentException("Invalid signature");

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    if(messageDto.isEncrypted()) {
      // Request Body 암호화
      try {
        requestBody = cryptoUtil.decrypt(messageDto.getMessage());
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        throw new IllegalArgumentException(e);
      }

      log.debug("Decrypted request body: {}", requestBody);
    } else {
      requestBody = messageDto.getMessage();
    }

    timeLog.info("Decrypted time(ms): {}, content length: {}"
        , (System.nanoTime() - start) / 1_000_000.0
        , messageDto.getMessage().length());

    return requestBody;
  }

  private MessageDto getBody(List<DataBuffer> dataBuffers) {
    StringBuilder bodyBuilder = new StringBuilder();
    dataBuffers.forEach(dataBuffer -> {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        bodyBuilder.append(new String(bytes, StandardCharsets.UTF_8));
    });

    String originalBody = bodyBuilder.toString();

    log.debug("Original body: {}", originalBody);

    return MessageDto.fromJson(originalBody);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  public static class Config {
    // 설정 추가가 필요하다면 여기에 정의
  }
}