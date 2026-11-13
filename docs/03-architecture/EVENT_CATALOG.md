# Event catalog (Phase 6, version 1)

All events share envelope version `1` and aggregate type `CustomerOrder`. Routing keys are stable wire contracts. Payloads are data-minimised and never contain credentials, session identifiers, CSRF tokens, email addresses, postal addresses or payment instrument data.

## Envelope

```json
{
  "eventId": "UUID",
  "eventType": "OrderPlaced",
  "eventVersion": 1,
  "aggregateType": "CustomerOrder",
  "aggregateId": "UUID",
  "occurredAt": "2026-11-13T10:15:30Z",
  "correlationId": "UUID or null",
  "causationId": "UUID or null",
  "payload": { }
}
```

| Field | Notes |
|---|---|
| `eventId` | Primary idempotency key; also the AMQP `messageId` and `eventId` header |
| `eventType` | Allow-listed wire name below |
| `eventVersion` | `1`; consumers reject other versions and quarantine them |
| `aggregateType` | `CustomerOrder` only |
| `aggregateId` | Order ID |
| `occurredAt` | Database transaction time (UTC instant) |
| `correlationId` | Currently the order ID for order-initiated flows |
| `causationId` | Reserved for future event-driven chains; null in Phase 6 |
| `payload` | Per-event fields below |

## Payload fields

Common order snapshot fields:

| Field | Type | Notes |
|---|---|---|
| `orderId` | UUID string | Matches `aggregateId` |
| `customerId` | UUID string | Recipient of in-app notifications |
| `orderNumber` | string | Human-readable order number, at most 32 characters |
| `status` | string | `PENDING_PAYMENT`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `PAYMENT_FAILED` |
| `paymentStatus` | string | `PENDING`, `SUCCEEDED`, `DECLINED`, `FAILED`, `UNKNOWN`, `VOIDED` |
| `totalAmount` | decimal string | Omitted on fulfilment transitions |
| `currency` | `USD` | Current single-currency implementation |
| `itemCount` | integer | Number of order lines, not quantity |
| `reason` | string | Optional, truncated to 200 characters; cancellation/transition reason |

## Events

| Wire name | Routing key | Emitted when | Status / payment status | Queue effect |
|---|---|---|---|---|
| `OrderPlaced` | `order.placed` | Simulated payment succeeded and checkout committed | `CONFIRMED` / `SUCCEEDED` | Notification "Order confirmed"; activity row |
| `OrderPaymentSucceeded` | `order.payment.succeeded` | Reserved for explicit payment-success flows; not emitted in Phase 6 because `OrderPlaced` already denotes a confirmed paid order | `CONFIRMED` / `SUCCEEDED` | Documented contract only |
| `OrderPaymentFailed` | `order.payment.failed` | Simulated payment declined/failed/timed out and reservations were released | `PAYMENT_FAILED` / simulator status | Notification "Payment failed"; activity row |
| `OrderCancelled` | `order.cancelled` | Customer or admin cancels an eligible confirmed order | `CANCELLED` / `VOIDED` | Notification "Order cancelled"; activity row |
| `OrderProcessingStarted` | `order.processing.started` | Admin moves a paid order to `PROCESSING`; reservations are consumed | `PROCESSING` / `SUCCEEDED` | Notification and activity row |
| `OrderShipped` | `order.shipped` | Admin moves `PROCESSING` to `SHIPPED` | `SHIPPED` / `SUCCEEDED` | Notification and activity row |
| `OrderDelivered` | `order.delivered` | Admin moves `SHIPPED` to `DELIVERED` | `DELIVERED` / `SUCCEEDED` | Notification and activity row |

`OrderPaymentSucceeded` is declared in the migration's allow-list and enum so a future explicit payment step can adopt it without a schema change. It is not produced today, and no consumer relies on it.

## Versioning policy

- Additive payload fields are backward compatible and do not bump the version.
- Removing/renaming a field, changing a type or meaning, or changing the event's business meaning requires a new `eventVersion`.
- Consumers support exactly the current version. An unsupported version is written to `dead_letter_messages` with `error_category=UNSUPPORTED_VERSION` and is not retried.
- Routing keys and queue names are treated as contracts. Renames require coordinated consumer deployment and a migration plan.
- The database `ck_outbox_event_type` constraint is the final allow-list; a new event type requires a Flyway migration and an ADR update.

## Delivery guarantees

- Producer: at-least-once from the transactional outbox. Duplicate `eventId` publication is expected after publisher crashes.
- Consumer: at-least-once from RabbitMQ; exactly one logical effect per `(eventId, consumerName)` because the inbox insert and effect share a transaction.
- Ordering is not guaranteed across events or consumers.
