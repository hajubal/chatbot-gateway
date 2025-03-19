package com.chatbot.gateway.filter.example;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 외부 서비스를 호출하여 요청 및 응답을 수정하는 필터 팩토리
 */
@Component
public class ExternalServiceModifierFilterFactory
    extends AbstractGatewayFilterFactory<ExternalServiceModifierFilterFactory.Config> {

  private static final Logger log = LoggerFactory.getLogger(ExternalServiceModifierFilterFactory.class);
  private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  private final WebClient webClient;

  @Autowired
  public ExternalServiceModifierFilterFactory(WebClient.Builder webClientBuilder,
      @Value("${filter.external-service.base-url:http://localhost:8090}") String baseUrl) {
    super(Config.class);
    this.webClient = webClientBuilder
        .baseUrl(baseUrl)
        .build();
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new ExternalModifierFilter(config);
  }

  private class ExternalModifierFilter implements GatewayFilter, Ordered {
    private final Config config;

    public ExternalModifierFilter(Config config) {
      this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      // 요청 수정이 활성화된 경우
      if (config.isModifyRequest() && isValidContentType(exchange.getRequest().getHeaders())) {
        return modifyRequestWithExternalService(exchange, chain);
      }
      // 응답 수정만 활성화된 경우
      else if (config.isModifyResponse()) {
        return modifyResponseWithExternalService(exchange, chain);
      }
      // 수정 없이 원본 요청/응답 처리
      else {
        return chain.filter(exchange);
      }
    }

    /**
     * 외부 서비스를 호출하여 요청 수정
     */
    private Mono<Void> modifyRequestWithExternalService(ServerWebExchange exchange, GatewayFilterChain chain) {
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
            String originalBody = new String(bytes, StandardCharsets.UTF_8);

            // 외부 서비스 요청 정보 생성
            ExternalServiceRequest serviceRequest = new ExternalServiceRequest();
            serviceRequest.setOriginalBody(originalBody);
            serviceRequest.setUri(request.getURI().toString());
            serviceRequest.setMethod(request.getMethod().name());
            serviceRequest.setHeaders(headersToMap(request.getHeaders()));
            serviceRequest.setRouteId(config.getRouteId());

            // 외부 서비스 호출
            return callExternalServiceForRequest(serviceRequest)
                .timeout(Duration.ofMillis(config.getTimeoutMillis()))
                .retryWhen(
                    Retry.backoff(config.getMaxRetries(), Duration.ofMillis(config.getRetryBackoffMillis()))
                        .filter(throwable -> throwable instanceof TimeoutException ||
                            !(throwable instanceof UnsupportedOperationException))
                )
                .onErrorResume(e -> {
                  log.error("Error calling external service for request modification", e);
                  // 오류 발생 시 원본 요청 본문 사용
                  return Mono.just(originalBody);
                })
                .flatMap(modifiedBody -> {
                  log.debug("Request body modified by external service: {} -> {} characters",
                      originalBody.length(), modifiedBody.length());

                  // 수정된 요청 본문을 캐시된 메시지에 쓰기
                  byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
                  DataBuffer modifiedDataBuffer = bufferFactory.wrap(modifiedBytes);

                  return Mono.just(modifiedDataBuffer)
                      .flatMap(buffer -> cachedBodyOutputMessage.writeWith(Flux.just(buffer)));
                });
          })
          .then(Mono.defer(() -> {
            // 요청 본문이 수정된 새로운 ServerHttpRequest 생성
            ServerHttpRequest modifiedRequest = decorateRequest(request, cachedBodyOutputMessage);

            // 응답 수정도 필요한 경우 응답 데코레이터 추가
            if (config.isModifyResponse()) {
              ServerHttpResponseDecorator responseDecorator = createResponseDecorator(exchange);

              ServerWebExchange modifiedExchange = exchange.mutate()
                  .request(modifiedRequest)
                  .response(responseDecorator)
                  .build();

              return chain.filter(modifiedExchange);
            } else {
              // 요청만 수정
              ServerWebExchange modifiedExchange = exchange.mutate()
                  .request(modifiedRequest)
                  .build();

              return chain.filter(modifiedExchange);
            }
          }))
          .onErrorResume(throwable -> {
            log.error("Error modifying request body with external service", throwable);
            return chain.filter(exchange); // 오류 발생시 원본 요청으로 계속 진행
          });
    }

    /**
     * 요청 데코레이터 생성
     */
    private ServerHttpRequest decorateRequest(ServerHttpRequest request,
        CachedBodyOutputMessage cachedBodyOutputMessage) {
      return new ServerHttpRequestDecorator(request) {
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
    }

    /**
     * 외부 서비스를 호출하여 응답만 수정
     */
    private Mono<Void> modifyResponseWithExternalService(ServerWebExchange exchange, GatewayFilterChain chain) {
      ServerHttpResponseDecorator responseDecorator = createResponseDecorator(exchange);

      ServerWebExchange decoratedExchange = exchange.mutate()
          .response(responseDecorator)
          .build();

      return chain.filter(decoratedExchange);
    }

    /**
     * 응답 데코레이터 생성
     */
    private ServerHttpResponseDecorator createResponseDecorator(ServerWebExchange exchange) {
      ServerHttpResponse originalResponse = exchange.getResponse();

      return new ServerHttpResponseDecorator(originalResponse) {
        @Override
        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
          // 컨텐츠 타입 체크
          if (!isValidContentType(getHeaders())) {
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

                  // 외부 서비스 요청 정보 생성
                  ExternalServiceResponse serviceResponse = new ExternalServiceResponse();
                  serviceResponse.setOriginalBody(originalResponseBody);
                  serviceResponse.setStatusCode(getStatusCode().value());
                  serviceResponse.setHeaders(headersToMap(getHeaders()));
                  serviceResponse.setRouteId(config.getRouteId());
                  serviceResponse.setRequestPath(exchange.getRequest().getPath().toString());

                  // 외부 서비스 호출
                  return callExternalServiceForResponse(serviceResponse)
                      .timeout(Duration.ofMillis(config.getTimeoutMillis()))
                      .retryWhen(
                          Retry.backoff(config.getMaxRetries(), Duration.ofMillis(config.getRetryBackoffMillis()))
                              .filter(throwable -> throwable instanceof TimeoutException ||
                                  !(throwable instanceof UnsupportedOperationException))
                      )
                      .onErrorResume(e -> {
                        log.error("Error calling external service for response modification", e);
                        // 오류 발생 시 원본 응답 본문 사용
                        return Mono.just(originalResponseBody);
                      })
                      .map(modifiedBody -> {
                        log.debug("Response body modified by external service: {} -> {} characters",
                            originalResponseBody.length(), modifiedBody.length());

                        // 수정된 응답을 DataBuffer로 변환
                        byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
                        DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);

                        // 응답 크기 헤더 갱신
                        getHeaders().setContentLength(modifiedBytes.length);

                        return modifiedBuffer;
                      });
                }));
          }

          // 원본 응답 그대로 리턴 (비정상 케이스)
          return super.writeWith(body);
        }
      };
    }

    /**
     * 외부 서비스를 호출하여 요청 본문 수정
     */
    private Mono<String> callExternalServiceForRequest(ExternalServiceRequest request) {
      return webClient.post()
          .uri(config.getRequestModificationEndpoint())
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromValue(request))
          .retrieve()
          .onStatus(httpStatusCode -> httpStatusCode.isError(), clientResponse ->
              clientResponse.bodyToMono(String.class)
                  .flatMap(errorBody -> Mono.error(
                      new RuntimeException("External service returned error: " +
                          clientResponse.statusCode() + ", body: " + errorBody))))
          .bodyToMono(ExternalServiceRequestResult.class)
          .map(ExternalServiceRequestResult::getModifiedBody);
    }

    /**
     * 외부 서비스를 호출하여 응답 본문 수정
     */
    private Mono<String> callExternalServiceForResponse(ExternalServiceResponse response) {
      return webClient.post()
          .uri(config.getResponseModificationEndpoint())
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromValue(response))
          .retrieve()
          .onStatus(httpStatusCode -> httpStatusCode.isError(), clientResponse ->
              clientResponse.bodyToMono(String.class)
                  .flatMap(errorBody -> Mono.error(
                      new RuntimeException("External service returned error: " +
                          clientResponse.statusCode() + ", body: " + errorBody))))
          .bodyToMono(ExternalServiceResponseResult.class)
          .map(ExternalServiceResponseResult::getModifiedBody);
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

    /**
     * 헤더를 Map으로 변환
     */
    private Map<String, List<String>> headersToMap(HttpHeaders headers) {
      return headers;
    }

    /**
     * 요청/응답 수정 가능한 컨텐츠 타입인지 확인
     */
    private boolean isValidContentType(HttpHeaders headers) {
      MediaType contentType = headers.getContentType();
      if (contentType == null) {
        return false;
      }

      return contentType.equals(MediaType.APPLICATION_JSON) ||
          contentType.equals(MediaType.APPLICATION_XML) ||
          contentType.equals(MediaType.TEXT_PLAIN) ||
          contentType.equals(MediaType.TEXT_XML) ||
          contentType.equals(MediaType.TEXT_HTML) ||
          contentType.toString().contains("application/json");
    }

    @Override
    public int getOrder() {
      return config.getFilterOrder();
    }
  }

  /**
   * 외부 서비스 요청 수정 DTO
   */
  public static class ExternalServiceRequest {
    private String originalBody;
    private String uri;
    private String method;
    private Map<String, List<String>> headers;
    private String routeId;

    // Getters and setters
    public String getOriginalBody() {
      return originalBody;
    }

    public void setOriginalBody(String originalBody) {
      this.originalBody = originalBody;
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public Map<String, List<String>> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
      this.headers = headers;
    }

    public String getRouteId() {
      return routeId;
    }

    public void setRouteId(String routeId) {
      this.routeId = routeId;
    }
  }

  /**
   * 외부 서비스 요청 수정 결과 DTO
   */
  public static class ExternalServiceRequestResult {
    private String modifiedBody;

    public String getModifiedBody() {
      return modifiedBody;
    }

    public void setModifiedBody(String modifiedBody) {
      this.modifiedBody = modifiedBody;
    }
  }

  /**
   * 외부 서비스 응답 수정 DTO
   */
  public static class ExternalServiceResponse {
    private String originalBody;
    private int statusCode;
    private Map<String, List<String>> headers;
    private String routeId;
    private String requestPath;

    // Getters and setters
    public String getOriginalBody() {
      return originalBody;
    }

    public void setOriginalBody(String originalBody) {
      this.originalBody = originalBody;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public void setStatusCode(int statusCode) {
      this.statusCode = statusCode;
    }

    public Map<String, List<String>> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
      this.headers = headers;
    }

    public String getRouteId() {
      return routeId;
    }

    public void setRouteId(String routeId) {
      this.routeId = routeId;
    }

    public String getRequestPath() {
      return requestPath;
    }

    public void setRequestPath(String requestPath) {
      this.requestPath = requestPath;
    }
  }

  /**
   * 외부 서비스 응답 수정 결과 DTO
   */
  public static class ExternalServiceResponseResult {
    private String modifiedBody;

    public String getModifiedBody() {
      return modifiedBody;
    }

    public void setModifiedBody(String modifiedBody) {
      this.modifiedBody = modifiedBody;
    }
  }

  /**
   * 필터 설정 클래스
   */
  public static class Config {
    private boolean modifyRequest = true;
    private boolean modifyResponse = true;
    private String requestModificationEndpoint = "/api/modify/request";
    private String responseModificationEndpoint = "/api/modify/response";
    private String routeId = "default";
    private int filterOrder = 50;
    private long timeoutMillis = 2000;
    private int maxRetries = 2;
    private long retryBackoffMillis = 100;

    public boolean isModifyRequest() {
      return modifyRequest;
    }

    public void setModifyRequest(boolean modifyRequest) {
      this.modifyRequest = modifyRequest;
    }

    public boolean isModifyResponse() {
      return modifyResponse;
    }

    public void setModifyResponse(boolean modifyResponse) {
      this.modifyResponse = modifyResponse;
    }

    public String getRequestModificationEndpoint() {
      return requestModificationEndpoint;
    }

    public void setRequestModificationEndpoint(String requestModificationEndpoint) {
      this.requestModificationEndpoint = requestModificationEndpoint;
    }

    public String getResponseModificationEndpoint() {
      return responseModificationEndpoint;
    }

    public void setResponseModificationEndpoint(String responseModificationEndpoint) {
      this.responseModificationEndpoint = responseModificationEndpoint;
    }

    public String getRouteId() {
      return routeId;
    }

    public void setRouteId(String routeId) {
      this.routeId = routeId;
    }

    public int getFilterOrder() {
      return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
      this.filterOrder = filterOrder;
    }

    public long getTimeoutMillis() {
      return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMillis() {
      return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
      this.retryBackoffMillis = retryBackoffMillis;
    }
  }
}

/**
 * 특정 서비스 요청에 대한 개인정보 마스킹 외부 서비스 호출 필터 구현 예시
 */
@Component
class ExternalPiiMaskingFilterFactory extends ExternalServiceModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(ExternalPiiMaskingFilterFactory.class);

  public ExternalPiiMaskingFilterFactory(WebClient.Builder webClientBuilder,
      @Value("${filter.pii-masking.base-url:http://privacy-service:8090}") String baseUrl) {
    super(webClientBuilder, baseUrl);
  }

  @Override
  public GatewayFilter apply(Config config) {
    // PII 마스킹 서비스에 맞는 설정 적용
    config.setRequestModificationEndpoint("/api/privacy/mask-request");
    config.setResponseModificationEndpoint("/api/privacy/mask-response");
    config.setRouteId("pii-masking");

    return super.apply(config);
  }

  /**
   * 기본 설정으로 필터 생성하는 정적 메서드
   */
  public static Config defaultConfig() {
    Config config = new Config();
    config.setFilterOrder(100);
    config.setTimeoutMillis(1500);
    config.setMaxRetries(3);
    return config;
  }
}

/**
 * 외부 AI 서비스를 통한 민감 정보 검출 및 마스킹 필터 예시
 */
@Component
class ExternalAiSensitiveInfoFilterFactory extends ExternalServiceModifierFilterFactory {

  private static final Logger log = LoggerFactory.getLogger(ExternalAiSensitiveInfoFilterFactory.class);

  public ExternalAiSensitiveInfoFilterFactory(WebClient.Builder webClientBuilder,
      @Value("${filter.ai-sensitive.base-url:http://ai-service:8091}") String baseUrl) {
    super(webClientBuilder, baseUrl);
  }

  @Override
  public GatewayFilter apply(Config config) {
    // AI 서비스에 맞는 설정 적용
    config.setRequestModificationEndpoint("/api/ai/scan-request");
    config.setResponseModificationEndpoint("/api/ai/scan-response");
    config.setRouteId("ai-sensitive-info");
    config.setTimeoutMillis(5000); // AI 서비스는 시간이 더 걸릴 수 있음

    return super.apply(config);
  }

  /**
   * 응답만 체크하는 설정으로 필터 생성하는 정적 메서드
   */
  public static Config responseOnlyConfig() {
    Config config = new Config();
    config.setModifyRequest(false);
    config.setModifyResponse(true);
    config.setFilterOrder(200);
    config.setTimeoutMillis(5000);
    config.setMaxRetries(2);
    return config;
  }
}

/**
 * 필터 구성 예시 (application.yml 또는 Java Config)
 */
/*
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder,
                                         ExternalPiiMaskingFilterFactory piiFilter,
                                         ExternalAiSensitiveInfoFilterFactory aiFilter) {
        return builder.routes()
            // 개인정보 처리 API 경로 - 외부 개인정보 마스킹 서비스 사용
            .route("personal-data-api", r -> r.path("/api/personal/**")
                .filters(f -> f.filter(piiFilter.apply(ExternalPiiMaskingFilterFactory.defaultConfig())))
                .uri("lb://personal-data-service"))

            // 금융 API 경로 - 외부 AI 민감정보 감지 서비스 사용 (응답만 체크)
            .route("finance-api", r -> r.path("/api/finance/**")
                .filters(f -> f.filter(aiFilter.apply(ExternalAiSensitiveInfoFilterFactory.responseOnlyConfig())))
                .uri("lb://finance-service"))
            .build();
    }
}
*/
