package com.shopsphere.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "qikink")
public class QikinkProperties {

    private String baseUrl = "https://api.qikink.com";
    private String tokenPath = "/api/token";
    private String orderListPath = "/api/order/list";
    private String clientId = "";
    private String clientSecret = "";
}
