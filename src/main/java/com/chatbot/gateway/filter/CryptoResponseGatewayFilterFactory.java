package com.chatbot.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;

import com.chatbot.gateway.dto.MessageDto;
import com.chatbot.gateway.util.CryptoUtil;
import com.chatbot.gateway.util.SignUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
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
 * ollama -> gateway -> streamlit
 *
 * gateway 에서 response encrypt 해서 streamlit으로 내려줌
 */
@Slf4j
@Component
public class CryptoResponseGatewayFilterFactory extends AbstractGatewayFilterFactory<CryptoResponseGatewayFilterFactory.Config>
        implements Ordered {

  Logger timeLog = LoggerFactory.getLogger("time");

  private final List<MediaType> streamingMediaTypes;

  private final CryptoUtil cryptoUtil;

  private final SignUtil signUtil;

  @Value("${app.message.encrypted:false}")
  private boolean isEncrypt;

  @Value("${app.message.signed:false}")
  private boolean isSign;

  @Value("${app.server.lineseparator:\n}")
  private String lineSeparator;

  private final MeterRegistry meterRegistry;

  private final AtomicLong contentLength = new AtomicLong(0);

  public CryptoResponseGatewayFilterFactory(List<MediaType> streamingMediaTypes
      , MeterRegistry meterRegistry
      , @Value("${app.message.enc_key}") String encKey
      , @Value("${app.message.private_key}") String privateKey
      , @Value("${app.message.public_key}") String publicKey
  ) {
    super(Config.class);
    this.streamingMediaTypes = streamingMediaTypes;
    this.cryptoUtil = new CryptoUtil(encKey);
    this.signUtil = new SignUtil(privateKey, publicKey);
    this.meterRegistry = meterRegistry;

    Gauge.builder("gateway.response.content.length", contentLength, AtomicLong::get)
        .register(meterRegistry);
  }

  @Override
  public GatewayFilter apply(Config config) {

    log.info("Init CryptoResponseGatewayFilterFactory.");

    return (exchange, chain) -> {
      log.info("Call CryptoResponseGatewayFilter.");
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
            log.trace("CryptoGatewayFilterFactory start inbound: {}, outbound: {}"
                , connection.channel().id().asShortText(), exchange.getLogPrefix());
          }

          ServerHttpResponse response = exchange.getResponse();

          final Flux<DataBuffer> body = getDataBuffer(connection, response);

          return (isStreamingMediaType(getMediaType(response))
              ? response.writeAndFlushWith(body.map(Flux::just)) : response.writeWith(body));
        })).doOnCancel(() -> cleanup(exchange))
        .doOnError(throwable -> cleanup(exchange))
//        .doOnSuccess(unused -> cleanup(exchange))
        ;
  }

  private static MediaType getMediaType(ServerHttpResponse response) {
    MediaType contentType = null;

    try {
      contentType = response.getHeaders().getContentType();
    } catch (Exception e) {
      log.error("invalid media type", e);
    }
    return contentType;
  }

  /**
   *
   * TODO: byteBuf 누수 확인
   * connection 으로 부터 받은 데이터를 암호화
   * @param connection
   * @param response
   * @return
   */
  private Flux<DataBuffer> getDataBuffer(Connection connection, ServerHttpResponse response) {
    StringBuffer sb = new StringBuffer();

    final Flux<DataBuffer> body = connection
        .inbound()
        .receive()
        .retain()
//      .map(byteBuf -> wrap(byteBuf, response)); //direct pass.
        .map(byteBuf -> { //data modify
          // Netty ByteBuf -> String 변환
          String original = byteBuf.toString(StandardCharsets.UTF_8);

          log.trace("Original Response Body: '{}'", original);

          if(original.endsWith(lineSeparator)) {
            //ollama로 부터 받은 데이터에 line separator가 있는 경우 암호화 문자열에서는 제거, ollama는 '\n' 으로 구분함
            log.trace("Contain line separator.");

            if(!sb.isEmpty()) {
              log.trace("Buffered string: '{}'", sb);

              original = sb + original;
              sb.setLength(0);
            }
          } else {
            //데이터 끝이 lien separator 가 없는 경우 ollama 로 부터 캐리지 리턴이 포함 될 때까지 client 에는 빈문자열을 내림
            log.trace("Not contain line separator.");

            sb.append(original);

            return wrap(Unpooled.copiedBuffer("", StandardCharsets.UTF_8), response);
          }

          MessageDto messageDto = handleMessage(original);

          log.debug("Response messageDto: {}", messageDto);

          //TODO: Need data buffer

          // String -> DataBuffer 변환
          return wrap(Unpooled.copiedBuffer(messageDto.toJson() + lineSeparator, StandardCharsets.UTF_8), response);
        });

    return body;
  }

  private MessageDto handleMessage(String original) {

    String encryptedData = original;

    long start = System.nanoTime();
    Timer.Sample sample = Timer.start(meterRegistry);

    // 데이터 암호화
    if(isEncrypt) {
      encryptedData = encryptData(original);
    }

    String signature = "";

    if(isSign) {
      signature = signData(encryptedData);
    }

    timeLog.info("Encrypted time(ms): {}, content size: {}"
        , (System.nanoTime() - start) / 1_000_000.0
        , original.length());

//    Timer.builder("gateway.response.processing.time")
//        .register(meterRegistry)
//        .record(() -> (System.nanoTime() - start) / 1_000_000.0);
    sample.stop(
        Timer.builder("gateway.response.processing.time")
            .register(meterRegistry)
    );

    contentLength.set(original.length());

    MessageDto messageDto = new MessageDto();
    messageDto.setEncrypted(isEncrypt);
    messageDto.setSigned(isSign);
    messageDto.setMessage(encryptedData);
    messageDto.setSignature(signature);

    return messageDto;
  }

  private String signData(String message) {
    try {
      return this.signUtil.sign(message);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String encryptData(String original) {
    try {
        timeLog.trace("Original: '{}'", original);

        String modified = cryptoUtil.encrypt(removeLineSeparator(original));

        log.debug("Encrypted response body: '{}'", modified);

        return modified;
    } catch (Exception e) {
      // TODO: error handling
      log.error("Response data encrypted file.", e);
      throw new RuntimeException(e);
    }
  }

  private String removeLineSeparator(String original) {
    return original.substring(0, original.lastIndexOf(lineSeparator));
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

  @Override
  public int getOrder() {
    return 2;
  }

  public static class Config {
    // 설정 추가가 필요하다면 여기에 정의
  }
}
