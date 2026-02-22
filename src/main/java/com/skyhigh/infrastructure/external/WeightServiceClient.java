package com.skyhigh.infrastructure.external;

import lombok.Builder;
import lombok.Data;

public interface WeightServiceClient {

    WeightResult getWeight(String baggageId);

    @Data
    @Builder
    class WeightResult {
        private String baggageId;
        private java.math.BigDecimal weightKg;
        private boolean valid;
    }
}
