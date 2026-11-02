package com.shopsphere.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;

public interface QikinkOrderService {
    JsonNode getOrders(String orderReferenceNo, String status, Long id, String ids, String params,
                       LocalDate liveDateMin, LocalDate liveDateMax, Integer pageNo);
}
