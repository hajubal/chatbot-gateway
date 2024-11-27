package com.chatbot.gateway.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaGatewayConfig {

  @Value("${host.url}")
  private String hostUrl;

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("ollama_route", r -> r.path("/api/chat")
            .uri(hostUrl + "/api/chat"))
        .route("generate", r -> r.path("/api/generate")
            .uri(hostUrl + "/api/generate"))
        .build();
  }
}
