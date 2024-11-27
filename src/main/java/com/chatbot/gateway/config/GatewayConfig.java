package com.chatbot.gateway.config;

import com.chatbot.gateway.filter.CryptoGatewayFilterFactory;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

  @Value("${host.url}")
  private String hostUrl;

  @Bean
  RouteLocator testRoutes(RouteLocatorBuilder builder, CryptoGatewayFilterFactory cryptoFilter) {
    CryptoGatewayFilterFactory.Config config = new CryptoGatewayFilterFactory.Config();

    GatewayFilter gatewayFilter = cryptoFilter.apply(config);
    return builder
        .routes()
        .route(predicateSpec -> predicateSpec
            .path("/api/chat")
            .filters(spec -> spec.filter(gatewayFilter))
            .uri(hostUrl))
        .build();
  }
}
