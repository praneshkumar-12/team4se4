package org.leap.service;

import org.leap.dto.OrderRequest;

public interface OrderPlacementService {

    void placeOrder(OrderRequest request);
}
