package com.chatbot.gateway.filter.example;


import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 요청 및 응답 본문을 안전하게 수정하는 필터 팩토리 기본 클래스
 */
@Component
public abstract class AbstractRequestResponseModifierFilterFactory
    extends AbstractGatewayFilterFactory<AbstractRequestResponseModifierFilterFactory.Config> {

  private static final Logger log = LoggerFactory.getLogger(AbstractRequestResponseModifierFilterFactory.class);
  private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  public AbstractRequestResponseModifierFilterFactory() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedRequestResponseModifierFilter(config);
  }

  /**
   * 각 구현체에서 실제 요청 수정 로직을 구현
   */
  protected abstract String modifyRequestBody(String originalBody, ServerWebExchange exchange);

  /**
   * 각 구현체에서 실제 응답 수정 로직을 구현
   */
  protected abstract String modifyResponseBody(String originalBody, ServerWebExchange exchange);

  /**
   * 필터 적용 순서를 정의 (낮은 값이 먼저 실행)
   */
  protected abstract int getFilterOrder();

  /**
   * 요청 본문 수정 적용 여부
   */
  protected boolean shouldModifyRequest() {
    return true;
  }

  /**
   * 응답 본문 수정 적용 여부
   */
  protected boolean shouldModifyResponse() {
    return true;
  }

  /**
   * 로깅해야 할 컨텐츠 타입인지 확인
   */
  protected boolean isLoggableContentType(HttpHeaders headers) {
    MediaType contentType = headers.getContentType();
    if (contentType == null) {
      return false;
    }

    return contentType.equals(MediaType.APPLICATION_JSON) ||
        contentType.equals(MediaType.APPLICATION_XML) ||
        contentType.equals(MediaType.APPLICATION_FORM_URLENCODED) ||
        contentType.equals(MediaType.TEXT_PLAIN) ||
        contentType.equals(MediaType.TEXT_XML) ||
        contentType.equals(MediaType.TEXT_HTML) ||
        contentType.toString().contains("application/json");
  }

  public static class Config {
    // 필요한 설정 속성들을 여기에 추가
    private boolean enableRequestModification = true;
    private boolean enableResponseModification = true;

    public boolean isEnableRequestModification() {
      return enableRequestModification;
    }

    public void setEnableRequestModification(boolean enableRequestModification) {
      this.enableRequestModification = enableRequestModification;
    }

    public boolean isEnableResponseModification() {
      return enableResponseModification;
    }

    public void setEnableResponseModification(boolean enableResponseModification) {
      this.enableResponseModification = enableResponseModification;
    }
  }

  private class OrderedRequestResponseModifierFilter implements GatewayFilter, Ordered {
    private final Config config;

    public OrderedRequestResponseModifierFilter(Config config) {
      this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      // 요청 및 응답을 모두 수정
      if (config.isEnableRequestModification() && shouldModifyRequest() &&
          isLoggableContentType(exchange.getRequest().getHeaders())) {
        return modifyRequestAndContinue(exchange, chain);
      } else if (config.isEnableResponseModification() && shouldModifyResponse()) {
        // 요청은 수정하지 않고 응답만 수정
        return modifyResponseOnly(exchange, chain);
      } else {
        // 수정 없이 원본 요청/응답 그대로 처리
        return chain.filter(exchange);
      }
    }

    /**
     * 요청을 수정하고 필터 체인 계속 진행
     */
    private Mono<Void> modifyRequestAndContinue(ServerWebExchange exchange, GatewayFilterChain chain) {
      ServerHttpRequest request = exchange.getRequest();

      // 원본 요청 헤더
      HttpHeaders headers = new HttpHeaders();
      headers.putAll(request.getHeaders());

      // 캐시를 사용하여 요청 본문을 읽고 수정
      CachedBodyOutputMessage cachedBodyOutputMessage = new CachedBodyOutputMessage(exchange, headers);

      return DataBufferUtils.join(request.getBody())
          .flatMap(dataBuffer -> {
            // 요청 본문을 문자열로 변환
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            String bodyString = new String(bytes, StandardCharsets.UTF_8);

            // 요청 본문을 수정
            String modifiedBody = modifyRequestBody(bodyString, exchange);

            // 수정된 요청 본문을 캐시된 메시지에 쓰기
            byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
            DataBuffer modifiedDataBuffer = bufferFactory.wrap(modifiedBytes);

            return Mono.just(modifiedDataBuffer)
                .flatMap(buffer -> cachedBodyOutputMessage.writeWith(Flux.just(buffer)));
          })
          .then(Mono.defer(() -> {
            // 요청 본문이 수정된 새로운 ServerHttpRequest 생성
            ServerHttpRequest modifiedRequest = new ServerHttpRequestDecorator(request) {
              @Override
              public Flux<DataBuffer> getBody() {
                return cachedBodyOutputMessage.getBody();
              }

              @Override
              public HttpHeaders getHeaders() {
                HttpHeaders modifiedHeaders = new HttpHeaders();
                modifiedHeaders.putAll(super.getHeaders());
                // Content-Length 헤더 업데이트
                modifiedHeaders.setContentLength(cachedBodyOutputMessage.getHeaders().getContentLength());
                return modifiedHeaders;
              }
            };

            // 응답 수정 필요한 경우 응답 데코레이터 추가
            if (config.isEnableResponseModification() && shouldModifyResponse()) {
              ServerHttpResponseDecorator responseDecorator = createResponseDecorator(exchange);

              // 수정된 요청 및 응답으로 교체
              ServerWebExchange modifiedExchange = exchange.mutate()
                  .request(modifiedRequest)
                  .response(responseDecorator)
                  .build();

              return chain.filter(modifiedExchange);
            } else {
              // 요청만 수정하고 응답은 그대로 유지
              ServerWebExchange modifiedExchange = exchange.mutate()
                  .request(modifiedRequest)
                  .build();

              return chain.filter(modifiedExchange);
            }
          }))
          .onErrorResume(throwable -> {
            log.error("Error modifying request body", throwable);
            return chain.filter(exchange); // 오류 발생시 원본 요청으로 계속 진행
          });
    }

    /**
     * 응답만 수정
     */
    private Mono<Void> modifyResponseOnly(ServerWebExchange exchange, GatewayFilterChain chain) {
      ServerHttpResponseDecorator responseDecorator = createResponseDecorator(exchange);

      ServerWebExchange modifiedExchange = exchange.mutate()
          .response(responseDecorator)
          .build();

      return chain.filter(modifiedExchange);
    }

    /**
     * 응답 데코레이터 생성
     */
    private ServerHttpResponseDecorator createResponseDecorator(ServerWebExchange exchange) {
      ServerHttpResponse originalResponse = exchange.getResponse();

      return new ServerHttpResponseDecorator(originalResponse) {
        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
          // 로깅 가능한 컨텐츠 타입인지 확인
          if (!isLoggableContentType(getHeaders())) {
            return super.writeWith(body);
          }

          if (body instanceof Flux) {
            Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

            return super.writeWith(fluxBody
                    .collectList()
                    .flatMap(dataBuffers -> {
                      // 모든 DataBuffer를 하나로 합치기
                      DataBuffer joinedBuffer = joinDataBuffers((List<DataBuffer>) dataBuffers);

                      // 원본 응답을 문자열로 변환
                      String originalResponseBody = joinedBuffer.toString(StandardCharsets.UTF_8);
                      DataBufferUtils.release(joinedBuffer);

                      // 응답 수정 로직 적용
                      String modifiedResponseBody = modifyResponseBody(originalResponseBody, exchange);

                      // 수정된 응답을 DataBuffer로 변환
                      byte[] modifiedBytes = modifiedResponseBody.getBytes(StandardCharsets.UTF_8);
                      DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);

                      // 응답 크기 헤더 갱신
                      originalResponse.getHeaders().setContentLength(modifiedBytes.length);

                      return Mono.just(modifiedBuffer);
                    }))
                .onErrorResume(throwable -> {
                  log.error("Error modifying response body", throwable);
                  return super.writeWith(body); // 오류 발생시 원본 응답 유지
                });
          }

          // 원본 응답 그대로 리턴 (비정상 케이스)
          return super.writeWith(body);
        }
      };
    }

    /**
     * DataBuffer 리스트를 하나의 DataBuffer로 합치기
     */
    private DataBuffer joinDataBuffers(List<DataBuffer> buffers) {
      int capacity = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
      DataBuffer joinedBuffer = bufferFactory.allocateBuffer(capacity);
      buffers.forEach(buffer -> {
        joinedBuffer.write(buffer);
        DataBufferUtils.release(buffer);
      });
      return joinedBuffer;
    }

    @Override
    public int getOrder() {
      return getFilterOrder();
    }
  }
}

/**
 * 개인정보 필터링 필터 구현 예시
 */
@Component
class PrivacyProtectionFilterFactory extends AbstractRequestResponseModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(PrivacyProtectionFilterFactory.class);
  private static final int ORDER = 100; // 가장 먼저 실행되는 필터

  @Override
  protected String modifyRequestBody(String originalBody, ServerWebExchange exchange) {
    log.debug("Applying privacy protection filter to request");
    // 요청 데이터에서 개인정보 필터링 로직
    return originalBody.replaceAll("\"ssn\"\\s*:\\s*\"\\d{3}-\\d{2}-\\d{4}\"", "\"ssn\":\"***-**-****\"")
        .replaceAll("\"creditcard\"\\s*:\\s*\"\\d{4}-\\d{4}-\\d{4}-\\d{4}\"", "\"creditcard\":\"****-****-****-****\"");
  }

  @Override
  protected String modifyResponseBody(String originalBody, ServerWebExchange exchange) {
    log.debug("Applying privacy protection filter to response");
    // 응답 데이터에서 개인정보 필터링 로직
    return originalBody.replaceAll("\\d{10,}", "**********");
  }

  @Override
  protected int getFilterOrder() {
    return ORDER;
  }

  @Override
  public List<String> shortcutFieldOrder() {
    return Collections.emptyList();
  }
}

/**
 * 민감 정보 필터링 필터 구현 예시
 */
@Component
class SensitiveInfoFilterFactory extends AbstractRequestResponseModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(SensitiveInfoFilterFactory.class);
  private static final int ORDER = 200; // 두 번째로 실행되는 필터

  @Override
  protected String modifyRequestBody(String originalBody, ServerWebExchange exchange) {
    log.debug("Applying sensitive info filter to request");
    // 요청에서 민감한 정보 마스킹
    return originalBody.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"*****\"")
        .replaceAll("\"apiKey\"\\s*:\\s*\"[^\"]*\"", "\"apiKey\":\"*****\"");
  }

  @Override
  protected String modifyResponseBody(String originalBody, ServerWebExchange exchange) {
    log.debug("Applying sensitive info filter to response");
    // 응답에서 민감한 정보 마스킹
    return originalBody
        .replaceAll("password\\s*:\\s*\"[^\"]*\"", "password: \"*****\"")
        .replaceAll("apiKey\\s*:\\s*\"[^\"]*\"", "apiKey: \"*****\"");
  }

  @Override
  protected int getFilterOrder() {
    return ORDER;
  }

  @Override
  public List<String> shortcutFieldOrder() {
    return Collections.emptyList();
  }
}

/**
 * 로깅 필터 구현 예시
 */
@Component
class RequestResponseLoggingFilterFactory extends AbstractRequestResponseModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilterFactory.class);
  private static final int ORDER = 300; // 세 번째로 실행되는 필터

  @Override
  protected String modifyRequestBody(String originalBody, ServerWebExchange exchange) {
    // 요청 내용만 로깅하고 수정하지 않음
    log.debug("Request body: {}",
        originalBody.length() > 100 ? originalBody.substring(0, 100) + "..." : originalBody);

    // 요청 메타 정보 로깅
    ServerHttpRequest request = exchange.getRequest();
    log.debug("Request: {} {}", request.getMethod(), request.getURI());
    log.debug("Request headers: {}", request.getHeaders());

    return originalBody;
  }

  @Override
  protected String modifyResponseBody(String originalBody, ServerWebExchange exchange) {
    // 응답 내용만 로깅하고 수정하지 않음
    log.debug("Response body: {}",
        originalBody.length() > 100 ? originalBody.substring(0, 100) + "..." : originalBody);

    // 응답 메타 정보 로깅
    ServerHttpResponse response = exchange.getResponse();
    log.debug("Response status: {}", response.getStatusCode());
    log.debug("Response headers: {}", response.getHeaders());

    return originalBody;
  }

  @Override
  protected int getFilterOrder() {
    return ORDER;
  }

  @Override
  public List<String> shortcutFieldOrder() {
    return Collections.emptyList();
  }
}

/**
 * API Key 추가 필터 예시
 */
@Component
class ApiKeyInjectionFilterFactory extends AbstractRequestResponseModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyInjectionFilterFactory.class);
  private static final int ORDER = 50; // 최우선 실행되는 필터

  @Override
  protected String modifyRequestBody(String originalBody, ServerWebExchange exchange) {
    // JSON 요청에 API 키 추가
    if (originalBody.startsWith("{") && originalBody.endsWith("}")) {
      // 마지막 중괄호 직전에 API 키 추가
      int lastBrace = originalBody.lastIndexOf("}");
      String modifiedBody = originalBody.substring(0, lastBrace)
          + (originalBody.length() > 2 ? ", " : "")
          + "\"apiKey\": \"INTERNAL_SYSTEM_API_KEY\""
          + originalBody.substring(lastBrace);
      return modifiedBody;
    }
    return originalBody;
  }

  @Override
  protected String modifyResponseBody(String originalBody, ServerWebExchange exchange) {
    // 응답은 수정하지 않음
    return originalBody;
  }

  @Override
  protected int getFilterOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldModifyResponse() {
    // 응답은 수정하지 않음
    return false;
  }

  @Override
  public List<String> shortcutFieldOrder() {
    return Collections.emptyList();
  }
}

/**
 * 오류 메시지 표준화 필터 예시
 */
@Component
class ErrorResponseStandardizerFilterFactory extends AbstractRequestResponseModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(ErrorResponseStandardizerFilterFactory.class);
  private static final int ORDER = 400; // 마지막으로 실행되는 필터

  @Override
  protected String modifyRequestBody(String originalBody, ServerWebExchange exchange) {
    // 요청은 수정하지 않음
    return originalBody;
  }

  @Override
  protected String modifyResponseBody(String originalBody, ServerWebExchange exchange) {
    // 오류 응답인 경우 형식 표준화
    HttpStatusCode status = exchange.getResponse().getStatusCode();

    if (status != null && status.isError() && originalBody.contains("error")) {
      // 간단한 예제: 오류 응답 형식 표준화
      try {
        // JSON 형식이 아니거나 이미 표준 형식인 경우 무시
        if (!originalBody.contains("standardError") && originalBody.contains("message")) {
          String modifiedBody = "{\n" +
              "  \"standardError\": true,\n" +
              "  \"statusCode\": " + status.value() + ",\n" +
              "  \"errorDetails\": " + originalBody + "\n" +
              "}";
          return modifiedBody;
        }
      } catch (Exception e) {
        log.warn("Failed to standardize error response", e);
      }
    }

    return originalBody;
  }

  @Override
  protected int getFilterOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldModifyRequest() {
    // 요청은 수정하지 않음
    return false;
  }

  @Override
  public List<String> shortcutFieldOrder() {
    return Collections.emptyList();
  }
}

/**
 * 필터 구성 예시 (application.yml 또는 Java Config)
 */
/*
spring:
  cloud:
    gateway:
      routes:
        - id: api-route
          uri: lb://api-service
          predicates:
            - Path=/api/**
          filters:
            - ApiKeyInjection
            - PrivacyProtection
            - SensitiveInfo
            - RequestResponseLogging
            - ErrorResponseStandardizer
*/
