package com.chatbot.gateway.config;

import com.chatbot.gateway.filter.CryptoResponseGatewayFilterFactory;

import com.chatbot.gateway.filter.CryptoRequestGatewayFilterFactory;
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
  RouteLocator testRoutes(RouteLocatorBuilder builder, CryptoResponseGatewayFilterFactory cryptoFilter
          , CryptoRequestGatewayFilterFactory requestFilterFactory) {

    GatewayFilter responseFilter = cryptoFilter.apply(new CryptoResponseGatewayFilterFactory.Config());
    GatewayFilter requestFilter = requestFilterFactory.apply(new CryptoRequestGatewayFilterFactory.Config());

    return builder
        .routes()
        .route(predicateSpec -> predicateSpec
            .path("/api/chat")
            .filters(spec -> spec.filters(requestFilter, responseFilter))
            .uri(hostUrl))
        .build();
  }
}
