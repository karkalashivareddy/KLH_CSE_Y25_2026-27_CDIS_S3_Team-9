# ADR-023: Cart changes do not reserve inventory

- **Status:** Accepted for Phase 4
- **Context:** A shopping cart is an expression of customer intent, not a stock commitment.
- **Decision:** Cart operations read current availability for display but do not call inventory reservation/mutation services or write inventory, reservation, movement, order, or payment data. Adding more than current availability is permitted; the response shows the availability so the UI can warn. Archived/inactive products cannot be newly added; existing lines remain visible with an unavailable flag and can be removed.
- **Consequence:** A future checkout must revalidate current price and availability and perform its own atomic stock/order workflow. Cart price and stock are advisory and may change.
