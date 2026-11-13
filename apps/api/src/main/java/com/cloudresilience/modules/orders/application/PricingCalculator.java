package com.cloudresilience.modules.orders.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PricingCalculator {
    public record PricedLine(BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal shipping, BigDecimal total) {}

    public List<PricedLine> price(List<PriceInput> lines) {
        return lines.stream().map(line -> {
            if (line.quantity() < 1 || line.quantity() > 9999) throw new IllegalArgumentException("Invalid quantity");
            BigDecimal unit = money(line.unitPrice());
            if (unit.signum() <= 0) throw new IllegalArgumentException("Invalid unit price");
            return new PricedLine(unit, line.quantity(), money(unit.multiply(BigDecimal.valueOf(line.quantity()))));
        }).toList();
    }

    public Totals totals(List<PricedLine> lines) {
        BigDecimal subtotal = money(lines.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = money(BigDecimal.ZERO);
        BigDecimal tax = money(BigDecimal.ZERO);
        BigDecimal shipping = money(BigDecimal.ZERO);
        return new Totals(subtotal, discount, tax, shipping, money(subtotal.subtract(discount).add(tax).add(shipping)));
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public record PriceInput(BigDecimal unitPrice, int quantity) {}
}
