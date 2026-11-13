package com.cloudresilience.modules.cart.api;

import com.cloudresilience.modules.cart.application.CartService;
import com.cloudresilience.modules.identity.application.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    public record ItemRequest(@NotNull UUID productId, @Min(1) @Max(9999) int quantity) {}
    public record QuantityRequest(@Min(1) @Max(9999) int quantity) {}

    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }

    @GetMapping
    public CartService.CartView get(@AuthenticationPrincipal UserPrincipal customer) {
        return carts.get(customer.id());
    }

    @GetMapping("/summary")
    public CartService.CartSummary summary(@AuthenticationPrincipal UserPrincipal customer) {
        return carts.summary(customer.id());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public CartService.CartView add(@AuthenticationPrincipal UserPrincipal customer, @Valid @RequestBody ItemRequest request) {
        return carts.add(customer.id(), request.productId(), request.quantity());
    }

    @PatchMapping("/items/{itemId}")
    public CartService.CartView update(@AuthenticationPrincipal UserPrincipal customer, @PathVariable UUID itemId,
            @Valid @RequestBody QuantityRequest request) {
        return carts.update(customer.id(), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartService.CartView remove(@AuthenticationPrincipal UserPrincipal customer, @PathVariable UUID itemId) {
        return carts.remove(customer.id(), itemId);
    }

    @DeleteMapping("/items")
    public CartService.CartView clear(@AuthenticationPrincipal UserPrincipal customer) {
        return carts.clear(customer.id());
    }
}
