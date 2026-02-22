package com.skyhigh.infrastructure.external;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@org.springframework.context.annotation.Primary
@ConditionalOnProperty(name = "skyhigh.weight.stub-enabled", havingValue = "true", matchIfMissing = true)
public class StubWeightServiceClient implements WeightServiceClient {

    @Override
    public WeightResult getWeight(String baggageId) {
        boolean overweight = baggageId != null && baggageId.toLowerCase().contains("overweight");
        return WeightResult.builder()
                .baggageId(baggageId)
                .weightKg(overweight ? new BigDecimal("30.00") : new BigDecimal("20.00"))
                .valid(true)
                .build();
    }
}
