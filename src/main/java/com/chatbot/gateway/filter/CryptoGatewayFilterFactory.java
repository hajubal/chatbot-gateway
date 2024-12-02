package com.chatbot.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

/**
 * 속도가 엄청 느려짐.... 왠지 다 받고 내려주는듯?
 */
@Slf4j
@Component
public class CryptoGatewayFilterFactory extends AbstractGatewayFilterFactory<CryptoGatewayFilterFactory.Config> {

  private final List<MediaType> streamingMediaTypes;

  public CryptoGatewayFilterFactory(List<MediaType> streamingMediaTypes) {
    super(Config.class);
    this.streamingMediaTypes = streamingMediaTypes;
  }

  @Override
  public GatewayFilter apply(Config config) {

    log.info("Init Test2Filter.");

    return (exchange, chain) -> {

      log.info("Call Test2Filter.");
      // 변경된 응답 객체로 교체
      return filter(exchange, chain);
    };
  }

  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain.filter(exchange)
        .then(Mono.defer(() -> {
          Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);

          if (connection == null) {
            return Mono.empty();
          }
          if (log.isTraceEnabled()) {
            log.trace("NettyWriteResponseFilter start inbound: "
                + connection.channel().id().asShortText() + ", outbound: "
                + exchange.getLogPrefix());
          }
          ServerHttpResponse response = exchange.getResponse();

          final Flux<DataBuffer> body = connection
              .inbound()
              .receive()
              .retain()
//              .map(byteBuf -> wrap(byteBuf, response));
              .map(byteBuf -> {
                // Netty ByteBuf -> String 변환
                String original = byteBuf.toString(StandardCharsets.UTF_8);
//                log.info("Original Response Body: {}", original);

                // 데이터 변조
                String modified = original.replace("apple", "got");
                log.info("Modified Response Body: {}", modified);

                // String -> DataBuffer 변환
                return wrap(Unpooled.copiedBuffer(modified, StandardCharsets.UTF_8), response);
              });

          log.info("call. 3");

          MediaType contentType = null;
          try {
            contentType = response.getHeaders().getContentType();
          }
          catch (Exception e) {
            if (log.isTraceEnabled()) {
              log.trace("invalid media type", e);
            }
          }

          return (isStreamingMediaType(contentType)
              ? response.writeAndFlushWith(body.map(Flux::just))
              : response.writeWith(body));
        })).doOnCancel(() -> cleanup(exchange))
        .doOnError(throwable -> cleanup(exchange));
  }

  private boolean isStreamingMediaType(@Nullable MediaType contentType) {
    if (contentType != null) {
      for (int i = 0; i < streamingMediaTypes.size(); i++) {
        if (streamingMediaTypes.get(i).isCompatibleWith(contentType)) {
          return true;
        }
      }
    }
    return false;
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

  private void cleanup(ServerWebExchange exchange) {
    Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);
    if (connection != null && connection.channel().isActive()) {
      connection.dispose();
    }
  }

  public static class Config {
    // 설정 추가가 필요하다면 여기에 정의
  }
}
