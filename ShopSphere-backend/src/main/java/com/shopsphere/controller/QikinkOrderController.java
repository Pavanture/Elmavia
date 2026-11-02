package com.shopsphere.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.dto.response.ApiResponse;
import com.shopsphere.service.QikinkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/integrations/qikink")
@RequiredArgsConstructor
@Tag(name = "Admin - Qikink", description = "Qikink order integration")
public class QikinkOrderController {

    private final QikinkOrderService qikinkOrderService;

    @GetMapping("/orders")
    @Operation(summary = "Fetch orders from Qikink")
    public ResponseEntity<ApiResponse<JsonNode>> getOrders(
            @RequestParam(required = false) String order_reference_no,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String ids,
            @RequestParam(required = false) String params,
            @RequestParam(required = false) LocalDate live_date_min,
            @RequestParam(required = false) LocalDate live_date_max,
            @RequestParam(required = false) Integer page_no) {
        JsonNode orders = qikinkOrderService.getOrders(
                order_reference_no, status, id, ids, params, live_date_min, live_date_max, page_no);
        return ResponseEntity.ok(ApiResponse.success("Qikink orders fetched successfully", orders));
    }
}
