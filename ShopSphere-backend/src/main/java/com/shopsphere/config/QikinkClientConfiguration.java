package com.shopsphere.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(QikinkProperties.class)
public class QikinkClientConfiguration {

    @Bean
    public WebClient qikinkWebClient(WebClient.Builder builder, QikinkProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
