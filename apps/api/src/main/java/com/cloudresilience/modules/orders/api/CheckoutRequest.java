package com.cloudresilience.modules.orders.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(@NotEmpty @Size(max = 100) List<@Valid Line> items) {
    public record Line(@NotNull UUID productId, @Min(1) @Max(9999) int quantity) {}
}
