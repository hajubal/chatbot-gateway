package com.chatbot.gateway.filter.example;

import com.chatbot.gateway.filter.example.AbstractModifyFilterFactory.Config;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public abstract class AbstractModifyFilterFactory extends AbstractGatewayFilterFactory<Config> {

    public AbstractModifyFilterFactory() {
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
     * 처기 가능한 컨텐츠 타입인지 확인
     */
    protected boolean isAcceptableContentType(HttpHeaders headers) {
      MediaType contentType = headers.getContentType();

      if (contentType == null) {
        log.info("컨텐츠 타입이 null");
        return false;
      }

      log.info("컨텐츠 타입 확인: {}", contentType);

      // JSON 타입에 대한 포괄적인 검사
      boolean isJson = contentType.includes(MediaType.APPLICATION_JSON) ||
          contentType.toString().toLowerCase().contains("json") ||
          contentType.toString().contains("+json");

      // TEXT 타입 검사
      boolean isText = contentType.includes(MediaType.TEXT_PLAIN) ||
          contentType.toString().toLowerCase().startsWith("text/");

      // XML 타입 검사
      boolean isXml = contentType.includes(MediaType.APPLICATION_XML) ||
          contentType.includes(MediaType.TEXT_XML) ||
          contentType.toString().toLowerCase().contains("xml");

      // HTML 타입 검사
      boolean isHtml = contentType.includes(MediaType.TEXT_HTML) ||
          contentType.toString().toLowerCase().contains("html");

      // 폼 데이터 검사
      boolean isForm = contentType.includes(MediaType.APPLICATION_FORM_URLENCODED) ||
          contentType.toString().toLowerCase().contains("form");

      boolean isAcceptable = isJson || isText || isXml || isHtml || isForm;

      log.info("컨텐츠 타입 허용 여부: {} (JSON={}, TEXT={}, XML={}, HTML={}, FORM={})",
          isAcceptable, isJson, isText, isXml, isHtml, isForm);

      return isAcceptable;
    }

    public static class Config {
      private boolean enable = true;

      public boolean isEnable() {
        return enable;
      }

      public void setEnable(boolean enable) {
        this.enable = enable;
      }

    }

    private class OrderedRequestResponseModifierFilter implements GatewayFilter, Ordered {
      private final Config config;

      public OrderedRequestResponseModifierFilter(Config config) {
        this.config = config;
      }

      @Override
      public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (config.isEnable()) {
          return modifyContent(exchange, chain);
        } else {
          // 수정 없이 원본 요청/응답 그대로 처리
          return chain.filter(exchange);
        }
      }

      // 아래 메서드를 추가하여 응답의 bufferFactory를 사용하도록 함
      protected DataBufferFactory getBufferFactory(ServerHttpResponse response) {
        return response.bufferFactory();
      }

      /**
       * 요청을 수정하고 필터 체인 계속 진행
       */
      // AbstractModifyFilterFactory 클래스 내부의 OrderedRequestResponseModifierFilter 클래스의
// modifyContent 메서드 부분을 다음과 같이 수정

      private Mono<Void> modifyContent(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.info("필터 시작: {}", request.getURI());

        // 수정 시작 - Body가 없는 경우 바로 필터 체인 실행
        if (request.getBody() == null) {
          log.info("요청 본문이 없음, 원본 요청 전달");
          return chain.filter(exchange);
        }

        // 새로운 헤더 객체 생성 (복사)
        HttpHeaders headers = new HttpHeaders();
        request.getHeaders().forEach((name, values) -> {
          values.forEach(value -> headers.add(name, value));
        });

        // 캐시를 사용하여 요청 본문을 읽고 수정
        CachedBodyOutputMessage cachedBodyOutputMessage = new CachedBodyOutputMessage(exchange, headers);

        return DataBufferUtils.join(request.getBody())
            .flatMap(dataBuffer -> {
              try {
                // 요청 본문을 문자열로 변환
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer); // 중요: 버퍼 릴리스
                String bodyString = new String(bytes, StandardCharsets.UTF_8);
                log.info("원본 요청 본문 읽기 완료: {} 바이트", bytes.length);

                // 요청 본문을 수정
                String modifiedBody = modifyRequestBody(bodyString, exchange);
                byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
                log.info("요청 본문 수정 완료: {} 바이트", modifiedBytes.length);

                // 수정된 요청 본문을 캐시된 메시지에 쓰기
                DataBufferFactory bufferFactory = getBufferFactory(exchange.getResponse());
                DataBuffer modifiedDataBuffer = bufferFactory.wrap(modifiedBytes);

                log.info("수정된 요청 본문 쓰기 시작");
                return cachedBodyOutputMessage.writeWith(Flux.just(modifiedDataBuffer))
                    .doOnSuccess(v -> log.info("요청 본문 쓰기 완료"))
                    .doOnError(e -> log.error("요청 본문 쓰기 오류", e))
                    .then(Mono.just(modifiedBody)); // 수정된 본문 전달
              } catch (Exception e) {
                log.error("요청 본문 처리 중 예외 발생", e);
                return Mono.error(e);
              }
            })
            .flatMap(modifiedBody -> {
              log.info("수정된 요청으로 새 요청 객체 생성");

              // 요청 본문이 수정된 새로운 ServerHttpRequest 생성
              ServerHttpRequest modifiedRequest = new ServerHttpRequestDecorator(request) {
                @Override
                public Flux<DataBuffer> getBody() {
                  log.info("수정된 요청 본문 요청됨");
                  return cachedBodyOutputMessage.getBody();
                }

                @Override
                public HttpHeaders getHeaders() {
                  HttpHeaders modifiedHeaders = new HttpHeaders();
                  modifiedHeaders.putAll(super.getHeaders());

                  // 중요: 수정된 본문의 실제 크기로 Content-Length 업데이트
                  byte[] modifiedBytes = modifiedBody.toString().getBytes(StandardCharsets.UTF_8);
                  modifiedHeaders.setContentLength(modifiedBytes.length);

                  log.info("요청 헤더 Content-Length 업데이트: 원본={}, 수정됨={}",
                      super.getHeaders().getContentLength(), modifiedBytes.length);
                  return modifiedHeaders;
                }
              };

              // ======= 응답 데코레이터 부분을 다음과 같이 수정 =======
              ServerHttpResponse originalResponse = exchange.getResponse();

              // 응답을 가로채서 처리하는 데코레이터
              ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                  log.info("응답 writeWith 호출됨, 상태: {}", getStatusCode());

                  if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    return super.writeWith(fluxBody
                        .collectList()
                        .flatMap(dataBuffers -> {
                          if (dataBuffers.isEmpty()) {
                            log.info("응답 데이터 버퍼가 비어있음");
                            return Mono.empty();
                          }

                          try {
                            // 모든 DataBuffer를 하나로 합치기
                            DataBuffer joinedBuffer =
                                joinDataBuffers(dataBuffers, originalResponse.bufferFactory());

                            if (joinedBuffer.readableByteCount() == 0) {
                              log.info("읽을 수 있는 응답 데이터가 없음");
                              return Mono.just(originalResponse.bufferFactory().wrap(new byte[0]));
                            }

                            // 원본 응답을 문자열로 변환
                            byte[] content = new byte[joinedBuffer.readableByteCount()];
                            joinedBuffer.read(content);
                            DataBufferUtils.release(joinedBuffer);

                            String responseBody = new String(content, StandardCharsets.UTF_8);
                            log.info("응답 본문 수정 시작 - 길이: {}", responseBody.length());

                            // 응답 내용이 매우 길 경우 일부만 로깅
                            if (responseBody.length() > 500) {
                              log.info("응답 원본 본문(일부): {}",
                                  responseBody.substring(0, 500) + "... (잘림)");
                            } else {
                              log.info("응답 원본 본문: {}", responseBody);
                            }

                            // 응답 수정 로직 적용
                            String modifiedResponseBody = modifyResponseBody(responseBody, exchange);
                            log.info("응답 본문 수정 완료");

                            // 수정된 응답을 DataBuffer로 변환
                            byte[] modifiedBytes = modifiedResponseBody.getBytes(StandardCharsets.UTF_8);
                            DataBuffer modifiedBuffer =
                                originalResponse.bufferFactory().wrap(modifiedBytes);

                            // 응답 크기 헤더 갱신
                            originalResponse.getHeaders().setContentLength(modifiedBytes.length);
                            log.info("응답 헤더 Content-Length 업데이트: {}", modifiedBytes.length);

                            return Mono.just(modifiedBuffer);
                          } catch (Exception e) {
                            log.error("응답 본문 수정 중 예외 발생", e);
                            if (!dataBuffers.isEmpty()) {
                              return Mono.just(dataBuffers.get(0));
                            }
                            return Mono.empty();
                          }
                        }));
                  }

                  log.info("응답이 Flux가 아닙니다: {}", body.getClass().getName());
                  return super.writeWith(body);
                }

                @Override
                public Mono<Void> writeAndFlushWith(
                    Publisher<? extends Publisher<? extends DataBuffer>> body) {
                  log.info("writeAndFlushWith 호출됨 (스트리밍 응답)");
                  return super.writeAndFlushWith(body);
                }
              };

              // 수정된 요청 및 응답으로 교체
              ServerWebExchange modifiedExchange = exchange.mutate()
                  .request(modifiedRequest)
                  .response(responseDecorator)
                  .build();

              log.info("필터 체인 계속 진행, 교체된 요청/응답 사용");
              return chain.filter(modifiedExchange)
                  .doOnSubscribe(s -> log.info("체인 필터 구독됨"))
                  .doOnSuccess(v -> log.info("체인 필터 성공"))
                  .doOnError(e -> log.error("체인 필터 오류", e))
                  .doFinally(signalType -> log.info("체인 필터 완료: {}", signalType));
            })
            .onErrorResume(throwable -> {
              log.error("요청 본문 수정 중 오류 발생", throwable);
              return chain.filter(exchange); // 오류 발생시 원본 요청으로 계속 진행
            });
      }

      /**
       * 응답 데코레이터 생성
       */
      private ServerHttpResponseDecorator createResponseDecorator(ServerWebExchange exchange) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        // 클래스에 정의된 getBufferFactory 메서드 활용
        DataBufferFactory bufferFactory = getBufferFactory(originalResponse);
        log.info("응답 데코레이터 생성, bufferFactory: {}", bufferFactory);

        return new ServerHttpResponseDecorator(originalResponse) {
          // writeWith 메서드 오버라이드 - 일반적인 응답 처리
          @Override
          public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            log.info("응답 writeWith 호출됨, 응답 상태: {}, 헤더: {}",
                getStatusCode(), getHeaders());

            // 컨텐츠 타입 로깅 추가 - 모든 응답에 대해 로깅
            MediaType contentType = getHeaders().getContentType();
            log.info("응답 컨텐츠 타입: {}", contentType);

            // 모든 종류의 응답을 처리
            if (body instanceof Flux) {
              Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

              return super.writeWith(fluxBody
                  .collectList()
                  .map(dataBuffers -> {
                    log.info("응답 데이터 버퍼 수집 완료: {} 개", dataBuffers.size());

                    if (dataBuffers.isEmpty()) {
                      log.info("응답 데이터 버퍼가 비어있음");
                      return bufferFactory.wrap(new byte[0]);
                    }

                    try {
                      // 모든 응답에 대해 내용 확인 시도
                      DataBuffer joinedBuffer = joinDataBuffers((List<DataBuffer>) dataBuffers, bufferFactory);
                      int readableBytes = joinedBuffer.readableByteCount();
                      log.info("응답 본문 버퍼 합치기 완료: {} 바이트", readableBytes);

                      if (readableBytes <= 0) {
                        log.info("응답 본문이 비어있음");
                        return bufferFactory.wrap(new byte[0]);
                      }

                      // 먼저 처리 가능한지 확인 (isAcceptableContentType 대신 직접 확인)
                      boolean canProcess = contentType != null &&
                          (contentType.includes(MediaType.APPLICATION_JSON) ||
                              contentType.toString().contains("json") ||
                              contentType.equals(MediaType.APPLICATION_XML) ||
                              contentType.equals(MediaType.TEXT_PLAIN) ||
                              contentType.equals(MediaType.TEXT_XML) ||
                              contentType.equals(MediaType.TEXT_HTML));

                      // 읽을 수 있는 내용이면 먼저 로그로 확인
                      if (canProcess) {
                        // 원본 응답을 문자열로 변환 (복사 후 변환)
                        byte[] content = new byte[readableBytes];
                        joinedBuffer.read(content);

                        // 원본 버퍼 보존을 위해 읽기 인덱스 리셋
                        joinedBuffer.readPosition(0);

                        String originalResponseBody = new String(content, StandardCharsets.UTF_8);
                        log.info("응답 본문 수정 시작 - 길이: {}", originalResponseBody.length());

                        // 응답 내용이 매우 길 경우 일부만 로깅
                        String logBody = originalResponseBody;
                        if (logBody.length() > 500) {
                          logBody = logBody.substring(0, 500) + "... (잘림)";
                        }
                        log.info("응답 원본 본문: {}", logBody);

                        // 처리 가능한 컨텐츠 타입인 경우 수정 로직 적용
                        if (isAcceptableContentType(getHeaders())) {
                          // 응답 수정 로직 적용
                          String modifiedResponseBody = modifyResponseBody(originalResponseBody, exchange);
                          log.info("수정된 응답 본문: {}",
                              modifiedResponseBody.length() > 500 ?
                                  modifiedResponseBody.substring(0, 500) + "... (잘림)" :
                                  modifiedResponseBody);

                          // 수정된 응답을 DataBuffer로 변환
                          byte[] modifiedBytes = modifiedResponseBody.getBytes(StandardCharsets.UTF_8);
                          DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);

                          // 응답 크기 헤더 갱신
                          getHeaders().setContentLength(modifiedBytes.length);
                          log.info("응답 헤더 Content-Length 업데이트: {}", modifiedBytes.length);

                          // 수정된 버퍼 반환
                          DataBufferUtils.release(joinedBuffer);
                          return modifiedBuffer;
                        } else {
                          log.info("수정 불가능한 컨텐츠 타입이지만 로깅 완료");
                        }
                      } else {
                        log.info("처리할 수 없는 컨텐츠 타입: {}", contentType);
                      }

                      // 처리할 수 없는 경우 원본 반환
                      return joinedBuffer;
                    } catch (Exception e) {
                      log.error("응답 처리 중 예외 발생", e);
                      // 에러 발생 시 원본 버퍼 반환
                      return dataBuffers.get(0);
                    }
                  })
                  .onErrorResume(throwable -> {
                    log.error("응답 본문 처리 중 오류 발생", throwable);
                    return ((Flux<? extends DataBuffer>) body).next();
                  }));
            } else if (body instanceof Mono) {
              // Mono 타입 응답 처리 추가
              log.info("Mono 타입 응답 처리");
              Mono<? extends DataBuffer> monoBody = (Mono<? extends DataBuffer>) body;

              return super.writeWith(monoBody.map(buffer -> {
                try {
                  int readableBytes = buffer.readableByteCount();
                  log.info("Mono 응답 버퍼 크기: {} 바이트", readableBytes);

                  if (readableBytes <= 0) {
                    return buffer;
                  }

                  // Mono도 처리 가능하면 내용 확인
                  if (isAcceptableContentType(getHeaders())) {
                    // 원본 응답을 문자열로 변환
                    byte[] content = new byte[readableBytes];
                    buffer.read(content);
                    // 읽기 위치 리셋
                    buffer.readPosition(0);

                    String originalResponseBody = new String(content, StandardCharsets.UTF_8);
                    log.info("Mono 응답 본문 수정 시작 - 길이: {}", originalResponseBody.length());

                    // 응답 내용이 매우 길 경우 일부만 로깅
                    String logBody = originalResponseBody;
                    if (logBody.length() > 500) {
                      logBody = logBody.substring(0, 500) + "... (잘림)";
                    }
                    log.info("Mono 응답 원본 본문: {}", logBody);

                    // 응답 수정 로직 적용
                    String modifiedResponseBody = modifyResponseBody(originalResponseBody, exchange);

                    // 수정된 응답을 DataBuffer로 변환
                    byte[] modifiedBytes = modifiedResponseBody.getBytes(StandardCharsets.UTF_8);
                    DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);

                    // 응답 크기 헤더 갱신
                    getHeaders().setContentLength(modifiedBytes.length);

                    // 원본 버퍼 해제
                    DataBufferUtils.release(buffer);
                    return modifiedBuffer;
                  }
                } catch (Exception e) {
                  log.error("Mono 응답 처리 중 예외 발생", e);
                }
                return buffer;
              }));
            }

            // 기타 타입의 응답 로깅
            log.info("처리할 수 없는 응답 타입: {}", body.getClass().getName());
            return super.writeWith(body);
          }

          // writeAndFlushWith 메서드도 오버라이드 (스트리밍 응답)
          @Override
          public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            log.info("writeAndFlushWith 호출됨 (스트리밍 응답)");
            return super.writeAndFlushWith(body);
          }

          // setComplete 메서드도 오버라이드 (응답이 비어있는 경우)
          @Override
          public Mono<Void> setComplete() {
            log.info("setComplete 호출됨 (빈 응답)");
            return super.setComplete();
          }
        };
      }

      /**
       * DataBuffer 리스트를 하나의 DataBuffer로 합치기
       */
      // DataBuffer 리스트를 하나의 DataBuffer로 합치는 보완된 메서드
      private DataBuffer joinDataBuffers(List<? extends DataBuffer> buffers, DataBufferFactory bufferFactory) {
        log.info("DataBuffer 합치기 시작, 버퍼 개수: {}", buffers.size());

        if (buffers.isEmpty()) {
          log.info("비어있는 버퍼 리스트, 빈 버퍼 반환");
          return bufferFactory.wrap(new byte[0]);
        }

        if (buffers.size() == 1) {
          log.info("버퍼가 하나만 있음, 그대로 반환");
          return buffers.get(0);
        }

        try {
          // 전체 용량 계산
          int capacity = buffers.stream()
              .mapToInt(DataBuffer::readableByteCount)
              .sum();

          log.info("병합할 총 버퍼 크기: {} 바이트", capacity);

          if (capacity <= 0) {
            log.info("버퍼 크기가 0 이하, 빈 버퍼 반환");
            return bufferFactory.wrap(new byte[0]);
          }

          // 새 버퍼 할당
          DataBuffer joinedBuffer = bufferFactory.allocateBuffer(capacity);

          // 각 버퍼의 내용을 새 버퍼에 복사 (동시에 릴리스하지 않음)
          for (DataBuffer buffer : buffers) {
            try {
              int readableCount = buffer.readableByteCount();
              if (readableCount > 0) {
                joinedBuffer.write(buffer);
              }
              // 주의: 여기서 버퍼를 해제하면 안됨 (호출자가 관리)
            } catch (Exception e) {
              log.error("버퍼 복사 중 오류 발생", e);
            }
          }

          return joinedBuffer;
        } catch (Exception e) {
          log.error("버퍼 병합 중 오류 발생", e);
          // 오류 발생 시 첫 번째 버퍼 반환
          return buffers.get(0);
        }
      }

      @Override
      public int getOrder() {
        return getFilterOrder();
      }
    }
  }