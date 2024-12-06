package com.chatbot.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;

import com.chatbot.gateway.util.CryptoUtil;
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

  public final CryptoUtil cryptoUtil;

  boolean isEncrypt = true;

  public CryptoGatewayFilterFactory(List<MediaType> streamingMediaTypes) {
    super(Config.class);
    this.streamingMediaTypes = streamingMediaTypes;
    this.cryptoUtil = new CryptoUtil("my_very_secret_key_32_bytes_long");
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
            log.trace("CryptoGatewayFilterFactory start inbound: "
                + connection.channel().id().asShortText() + ", outbound: "
                + exchange.getLogPrefix());
          }

          ServerHttpResponse response = exchange.getResponse();

          StringBuffer sb = new StringBuffer();

          final Flux<DataBuffer> body = connection
              .inbound()
              .receive()
              .retain()
//              .map(byteBuf -> wrap(byteBuf, response)); //direct pass.
              .map(byteBuf -> { //data modify
                // Netty ByteBuf -> String 변환
                String original = byteBuf.toString(StandardCharsets.UTF_8);
                log.trace("Original Response Body: {}", original);

                String modified = "";

                // 데이터 변조
//                modified = original.toLowerCase().replace("apple", "got");

                // 데이터 암호화
                try {
                  if(isEncrypt) {
                    log.info("Original Response Encrypted.");

                    long start = System.currentTimeMillis();

                    //LLM으로 부터 캐리지 리턴이 있는 경우 암호화 문자열에서는 제거
                    if(original.endsWith(System.lineSeparator())) {

                      if(!sb.isEmpty()) {
                        original = sb + original;
                        sb.setLength(0);
                      }

                      modified = cryptoUtil.encrypt(removeLineSeparator(original)) + System.lineSeparator();
                    } else {
//                      modified = cryptoUtil.encrypt(original);
                      sb.append(original);

                      wrap(Unpooled.copiedBuffer("", StandardCharsets.UTF_8), response);
                    }

                    long end = System.currentTimeMillis();

                    log.trace("Encrypted Time: {} ms", end - start);

                    log.debug("Encrypted Response Body: {}", modified);
                  } else {
                    modified = original;
                  }

                } catch (Exception e) {
                  // TODO: error handling
                  log.error("Response data encrypted file.", e);
                  throw new RuntimeException(e);
                }

                //TODO: data buffer 추가

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

  private static String removeLineSeparator(String original) {
    return original.substring(0, original.lastIndexOf(System.lineSeparator()));
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
