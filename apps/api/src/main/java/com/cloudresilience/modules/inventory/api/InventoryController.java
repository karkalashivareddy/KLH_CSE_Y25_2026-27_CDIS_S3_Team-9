package com.cloudresilience.modules.inventory.api;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.inventory.application.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryController {
    private final InventoryService inventory; private final JdbcTemplate jdbc;
    public InventoryController(InventoryService inventory,JdbcTemplate jdbc) { this.inventory=inventory; this.jdbc=jdbc; }
    public record InitialInput(@PositiveOrZero long quantity,@NotBlank @Size(max=500) String reason) {}
    public record AdjustmentInput(@NotNull Long delta,@NotBlank @Size(max=500) String reason) {}
    public record ReservationInput(@Positive long quantity,@NotBlank @Size(max=500) String reason) {}

    @PostMapping("/{productId}") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> initialize(@PathVariable UUID productId,@Valid @RequestBody InitialInput in,@AuthenticationPrincipal UserPrincipal user) { return inventory.initialize(productId,in.quantity(),in.reason(),user.id()); }
    @GetMapping("/{productId}") Map<String,Object> get(@PathVariable UUID productId) { return Map.of("inventory",inventory.inventory(productId),"movements",inventory.movements(productId)); }
    @PostMapping("/{productId}/adjustments") Map<String,Object> adjust(@PathVariable UUID productId,@Valid @RequestBody AdjustmentInput in,@AuthenticationPrincipal UserPrincipal user) { return inventory.adjust(productId,in.delta(),in.reason(),user.id()); }
    @PostMapping("/{productId}/reservations") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> reserve(@PathVariable UUID productId,@Valid @RequestBody ReservationInput in,@AuthenticationPrincipal UserPrincipal user) { return inventory.reserve(productId,in.quantity(),in.reason(),user.id()); }
    @PostMapping("/{productId}/reservations/{reservationId}/release") Map<String,Object> release(@PathVariable UUID productId,@PathVariable UUID reservationId,@RequestBody(required=false) Map<String,String> body,@AuthenticationPrincipal UserPrincipal user) { String reason=body==null||body.get("reason")==null?"Reservation released":body.get("reason"); if(reason.isBlank()||reason.length()>500) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid reason"); return inventory.release(productId,reservationId,reason,user.id()); }
    @GetMapping Map<String,Object> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        if(page<0||size<1||size>100) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");
        long total=jdbc.queryForObject("SELECT count(*) FROM inventory",Long.class); var rows=jdbc.queryForList("SELECT p.id product_id,p.sku,p.name,i.quantity_on_hand,i.quantity_reserved,i.quantity_on_hand-i.quantity_reserved available_quantity FROM inventory i JOIN products p ON p.id=i.product_id ORDER BY p.sku,p.id LIMIT ? OFFSET ?",size,(long)page*size);
        return Map.of("content",rows,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
    }
}
