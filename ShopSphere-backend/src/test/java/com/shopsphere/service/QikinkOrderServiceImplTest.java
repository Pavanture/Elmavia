package com.shopsphere.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.config.QikinkProperties;
import com.shopsphere.exception.QikinkConfigurationException;
import com.shopsphere.service.impl.QikinkOrderServiceImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QikinkOrderServiceImplTest {

    private final AtomicReference<String> tokenRequestBody = new AtomicReference<>();
    private final AtomicReference<String> orderRequestQuery = new AtomicReference<>();
    private final AtomicReference<String> orderClientId = new AtomicReference<>();
    private final AtomicReference<String> orderAccessToken = new AtomicReference<>();

    private HttpServer server;
    private QikinkProperties properties;
    private QikinkOrderServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"Accesstoken\":\"test-access-token\"}");
        });
        server.createContext("/api/order/list", exchange -> {
            orderRequestQuery.set(exchange.getRequestURI().getRawQuery());
            orderClientId.set(exchange.getRequestHeaders().getFirst("ClientId"));
            orderAccessToken.set(exchange.getRequestHeaders().getFirst("Accesstoken"));
            respond(exchange, 200, "{\"pagination\":{\"page_no\":2},\"orders\":[]}");
        });
        server.start();

        properties = new QikinkProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        service = new QikinkOrderServiceImpl(
                WebClient.builder().baseUrl(properties.getBaseUrl()).build(),
                properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void getOrdersExchangesCredentialsAndSendsQueryFiltersAndAuthenticationHeaders() {
        JsonNode orders = service.getOrders(
                "ref-123", "Live", null, null, "order_id,status",
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), 2);

        assertEquals(2, orders.path("pagination").path("page_no").asInt());
        assertEquals("ClientId=test-client-id&client_secret=test-client-secret", tokenRequestBody.get());
        assertEquals("test-client-id", orderClientId.get());
        assertEquals("test-access-token", orderAccessToken.get());
        assertTrue(orderRequestQuery.get().contains("order_reference_no=ref-123"),
                () -> "Unexpected order query: " + orderRequestQuery.get());
        assertTrue(orderRequestQuery.get().contains("status=Live"));
        assertTrue(orderRequestQuery.get().contains("params=order_id,status")
                || orderRequestQuery.get().contains("params=order_id%2Cstatus"));
        assertTrue(orderRequestQuery.get().contains("live_date_min=2026-10-01"));
        assertTrue(orderRequestQuery.get().contains("live_date_max=2026-10-31"));
        assertTrue(orderRequestQuery.get().contains("page_no=2"));
    }

    @Test
    void getOrdersRejectsInvalidPageNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getOrders(null, null, null, null, null, null, null, 0));
    }

    @Test
    void getOrdersRejectsAReversedDateRange() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getOrders(null, null, null, null, null,
                        LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01"), null));
    }

    @Test
    void getOrdersRejectsAnAbsoluteTokenUrl() {
        properties.setTokenPath("https://example.invalid/api/token");

        assertThrows(QikinkConfigurationException.class,
                () -> service.getOrders(null, null, null, null, null, null, null, null));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }
}
