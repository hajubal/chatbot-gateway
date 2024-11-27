package com.chatbot.gateway.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class OllamaGatewayConfig {

  @Value("${host.url}")
  private String hostUrl;

  /**
   * request, response data를 핸들링 할 수 있으나, response의 경우 stream으로 처리할 수 없다.
   * @param builder
   * @return
   */
  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("ollama_route", r -> r.path("/api/chat")
            .filters(f -> {
              f.modifyRequestBody(String.class, String.class, (exchange, body) -> {

                log.info("Request body: {}", body);

                return Mono.just(body);
              });

              f.modifyResponseBody(String.class, String.class, (exchange, body) -> {

                log.info("Response body: {}", body);

                return Mono.just(body);
              });

              return f;
            })
            .uri(hostUrl + "/api/chat"))
        .route("generate", r -> r.path("/api/generate")
            .uri(hostUrl + "/api/generate"))
        .build();
  }
}
