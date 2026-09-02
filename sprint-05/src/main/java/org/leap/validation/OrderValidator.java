package org.leap.validation;

import org.leap.dto.OrderRequest;

public interface OrderValidator {

    void validate(OrderRequest request);
}
