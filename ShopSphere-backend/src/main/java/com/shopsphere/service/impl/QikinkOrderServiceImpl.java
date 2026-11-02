package com.shopsphere.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.config.QikinkProperties;
import com.shopsphere.exception.QikinkApiException;
import com.shopsphere.exception.QikinkConfigurationException;
import com.shopsphere.service.QikinkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class QikinkOrderServiceImpl implements QikinkOrderService {

    private final WebClient qikinkWebClient;
    private final QikinkProperties qikinkProperties;

    @Override
    public JsonNode getOrders(String orderReferenceNo, String status, Long id, String ids, String params,
                              LocalDate liveDateMin, LocalDate liveDateMax, Integer pageNo) {
        validateRequest(orderReferenceNo, liveDateMin, liveDateMax, pageNo);
        validateConfiguration();

        String accessToken = requestAccessToken();
        JsonNode orders = qikinkWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(qikinkProperties.getOrderListPath());
                    addQueryParameter(uriBuilder, "order_reference_no", orderReferenceNo);
                    addQueryParameter(uriBuilder, "status", status);
                    addQueryParameter(uriBuilder, "id", id);
                    addQueryParameter(uriBuilder, "ids", ids);
                    addQueryParameter(uriBuilder, "params", params);
                    addQueryParameter(uriBuilder, "live_date_min", liveDateMin);
                    addQueryParameter(uriBuilder, "live_date_max", liveDateMax);
                    addQueryParameter(uriBuilder, "page_no", pageNo);
                    return uriBuilder.build();
                })
                .header("ClientId", qikinkProperties.getClientId())
                .header("Accesstoken", accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    int responseStatus = response.statusCode().value();
                    return response.releaseBody()
                            .then(Mono.error(new QikinkApiException(
                                    "Qikink order list request failed with HTTP " + responseStatus)));
                })
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class,
                        ex -> new QikinkApiException("Unable to connect to the Qikink API", ex))
                .block();
        if (orders == null) {
            throw new QikinkApiException("Qikink order list response body was empty");
        }
        return orders;
    }

    private String requestAccessToken() {
        JsonNode tokenResponse = qikinkWebClient.post()
                .uri(qikinkProperties.getTokenPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("ClientId", qikinkProperties.getClientId())
                        .with("client_secret", qikinkProperties.getClientSecret()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    int responseStatus = response.statusCode().value();
                    return response.releaseBody()
                            .then(Mono.error(new QikinkApiException(
                                    "Qikink token request failed with HTTP " + responseStatus)));
                })
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class,
                        ex -> new QikinkApiException("Unable to connect to the Qikink API", ex))
                .block();

        JsonNode accessToken = tokenResponse == null ? null : tokenResponse.get("Accesstoken");
        if (accessToken == null || !StringUtils.hasText(accessToken.asText())) {
            throw new QikinkApiException("Qikink token response did not contain an access token");
        }
        return accessToken.asText();
    }

    private void validateRequest(String orderReferenceNo, LocalDate liveDateMin,
                                 LocalDate liveDateMax, Integer pageNo) {
        if (orderReferenceNo != null && orderReferenceNo.length() > 22) {
            throw new IllegalArgumentException("order_reference_no must not exceed 22 characters");
        }
        if (pageNo != null && pageNo < 1) {
            throw new IllegalArgumentException("page_no must be at least 1");
        }
        if (liveDateMin != null && liveDateMax != null && liveDateMin.isAfter(liveDateMax)) {
            throw new IllegalArgumentException("live_date_min must not be after live_date_max");
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(qikinkProperties.getClientId())
                || !StringUtils.hasText(qikinkProperties.getClientSecret())) {
            throw new QikinkConfigurationException(
                    "Qikink credentials are not configured. Set QIKINK_CLIENT_ID and QIKINK_CLIENT_SECRET.");
        }
        validateRelativePath(qikinkProperties.getTokenPath(), "QIKINK_TOKEN_PATH");
        validateRelativePath(qikinkProperties.getOrderListPath(), "QIKINK_ORDER_LIST_PATH");
    }

    private void validateRelativePath(String path, String propertyName) {
        if (!StringUtils.hasText(path)) {
            throw new QikinkConfigurationException(propertyName + " is not configured.");
        }
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            throw new QikinkConfigurationException(
                    propertyName + " must be a relative path beginning with a single slash.");
        }
    }

    private void addQueryParameter(org.springframework.web.util.UriBuilder uriBuilder,
                                   String name, Object value) {
        if (value != null) {
            uriBuilder.queryParam(name, value);
        }
    }
}
