package com.cloudresilience.modules.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudresilience.modules.orders.application.PricingCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {
    private final PricingCalculator pricing = new PricingCalculator();

    @Test
    void calculatesExactDecimalSnapshotsAndServerTotals() {
        var lines = pricing.price(List.of(new PricingCalculator.PriceInput(new BigDecimal("12.35"), 3),
                new PricingCalculator.PriceInput(new BigDecimal("0.99"), 2)));
        var totals = pricing.totals(lines);
        assertThat(lines).extracting(PricingCalculator.PricedLine::lineTotal)
                .containsExactly(new BigDecimal("37.05"), new BigDecimal("1.98"));
        assertThat(totals.subtotal()).isEqualByComparingTo("39.03");
        assertThat(totals.total()).isEqualByComparingTo("39.03");
    }

    @Test
    void rejectsFloatingScaleAndInvalidQuantityInsteadOfRoundingSilently() {
        assertThatThrownBy(() -> pricing.price(List.of(new PricingCalculator.PriceInput(new BigDecimal("1.001"), 1))))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> pricing.price(List.of(new PricingCalculator.PriceInput(new BigDecimal("1.00"), 10000))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
