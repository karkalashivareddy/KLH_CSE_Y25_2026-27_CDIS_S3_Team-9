import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, cancelOrder, checkout, getAdminOrder, getAdminOrderSummary, getAdminOrders, getOrder, getOrders, updateOrderStatus, type Order, type OrderStatus, type OrderSummary } from './api'
import { useCart } from './cart'

const statuses: OrderStatus[] = ['PENDING_PAYMENT', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'PAYMENT_FAILED']
const money = (value: number, currency: string) => `${currency} ${Number(value).toFixed(2)}`
const pretty = (value: string) => value.toLowerCase().replaceAll('_', ' ')

function OrderItems({ order }: { order: Order }) {
  return <div className="order-items">{order.items.map((item) => <article className="order-line" key={item.itemId}>
    <div><strong>{item.productName}</strong><small>{item.sku} · {item.quantity} × {money(item.unitPrice, order.currency)}</small></div>
    <strong>{money(item.lineTotal, order.currency)}</strong>
  </article>)}</div>
}

function OrderCard({ order }: { order: Order }) {
  return <article className="order-card"><div className="order-card-heading"><div><span className="eyebrow">{order.orderNumber}</span><h2>{new Date(order.createdAt).toLocaleString()}</h2></div><span className={`order-status status-${order.status.toLowerCase().replaceAll('_', '-')}`}>{pretty(order.status)}</span></div>
    <OrderItems order={order} /><div className="order-card-footer"><span>{order.items.reduce((sum, item) => sum + item.quantity, 0)} items · {money(order.totalAmount, order.currency)}</span><Link to={`/orders/${order.id}`}>View order <span aria-hidden="true">→</span></Link></div>
  </article>
}

export function OrderHistoryPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true)
    try { setOrders((await getOrders()).content); setError('') }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Orders could not be loaded.') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { void Promise.resolve().then(load) }, [load])
  return <div className="page-content orders-page"><span className="eyebrow">CUSTOMER ACCOUNT</span><h1>Your orders</h1><p className="page-lead">Order history and status updates for your purchases.</p>
    {error && <p className="form-error" role="alert">{error} <button className="text-button" onClick={() => void load()}>Retry</button></p>}
    {loading ? <p role="status">Loading your orders…</p> : orders.length ? <section className="order-list" aria-label="Order history">{orders.map((order) => <OrderCard key={order.id} order={order} />)}</section> : <section className="empty-state"><h2>No orders yet</h2><p>Your completed checkouts will appear here.</p><Link className="button-link" to="/catalog">Browse catalog</Link></section>}
  </div>
}

export function OrderDetailPage() {
  const { id = '' } = useParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState('')
  const navigate = useNavigate()
  const load = useCallback(async () => {
    setLoading(true)
    try { setOrder(await getOrder(id)); setError('') }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Order could not be loaded.') }
    finally { setLoading(false) }
  }, [id])
  useEffect(() => { void Promise.resolve().then(load) }, [load])
  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError(''); setNotice('')
    try { await cancelOrder(id, reason); setNotice('Order cancelled. Its inventory reservation was released.'); setReason(''); await load() }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Cancellation failed.') }
    finally { setPending(false) }
  }
  if (loading) return <div className="page-content orders-page"><p role="status">Loading order…</p></div>
  if (!order) return <div className="page-content orders-page"><h1>Order unavailable</h1><p role="alert">{error}</p><button onClick={() => void load()}>Retry</button></div>
  const cancellable = order.status === 'CONFIRMED' && order.paymentStatus === 'SUCCEEDED'
  return <div className="page-content orders-page"><Link className="back-link" to="/orders">← All orders</Link><span className="eyebrow">{order.orderNumber}</span><h1>Order details</h1>
    {error && <p className="form-error" role="alert">{error}</p>}{notice && <p className="form-notice" role="status">{notice}</p>}
    <section className="order-detail-card"><div className="order-card-heading"><div><h2>{pretty(order.status)}</h2><p>Placed {new Date(order.createdAt).toLocaleString()}</p></div><span className="order-status">Payment {pretty(order.paymentStatus)}</span></div>
      <OrderItems order={order} /><dl className="order-totals"><div><dt>Subtotal</dt><dd>{money(order.subtotal, order.currency)}</dd></div><div><dt>Discount</dt><dd>−{money(order.discountAmount, order.currency)}</dd></div><div><dt>Tax</dt><dd>{money(order.taxAmount, order.currency)}</dd></div><div><dt>Shipping</dt><dd>{money(order.shippingAmount, order.currency)}</dd></div><div className="order-grand-total"><dt>Total</dt><dd>{money(order.totalAmount, order.currency)}</dd></div></dl>
      {order.status === 'PAYMENT_FAILED' && <p className="planned-banner">Simulated payment did not complete. No money was charged. Review your cart to start a new checkout.</p>}
      {cancellable && <form className="cancel-order-form" onSubmit={(event) => void submit(event)}><label htmlFor="cancel-reason">Cancellation reason</label><input id="cancel-reason" required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /><button className="text-button" disabled={pending}>{pending ? 'Cancelling…' : 'Cancel order'}</button><small>Only confirmed orders can be cancelled before processing. Payment is simulated; cancellation does not issue a real refund.</small></form>}
    </section>
    <section className="order-timeline"><h2>Status history</h2>{order.history.map((event) => <article key={event.eventId}><span className="timeline-dot" /><div><strong>{pretty(event.toStatus)}</strong><small>{new Date(event.createdAt).toLocaleString()}{event.reason ? ` · ${event.reason}` : ''}</small></div></article>)}</section>
    <button className="text-button" onClick={() => navigate('/orders')}>Back to orders</button>
  </div>
}

export function CheckoutPage() {
  const { cart, refresh } = useCart()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const idempotencyKey = useRef('')
  const navigate = useNavigate()
  async function submit() {
    if (!cart?.items.length) return
    setPending(true); setError('')
    if (!idempotencyKey.current) idempotencyKey.current = globalThis.crypto?.randomUUID?.() ?? `checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`
    try {
      const order = await checkout(cart.items.map(({ productId, quantity }) => ({ productId, quantity })), idempotencyKey.current)
      await refresh()
      navigate(`/orders/${order.id}`, { replace: true, state: { checkoutComplete: true } })
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status === 409 ? `${failure.message} Your cart has not been ordered.` : failure instanceof Error ? failure.message : 'Checkout failed.')
    } finally { setPending(false) }
  }
  if (!cart?.items.length) return <div className="page-content orders-page"><h1>Nothing to check out</h1><p>Your cart is empty. Add items before starting checkout.</p><Link className="button-link" to="/catalog">Browse catalog</Link></div>
  return <div className="page-content orders-page"><Link className="back-link" to="/cart">← Back to cart</Link><span className="eyebrow">SECURE CHECKOUT</span><h1>Review and place order</h1><p className="page-lead">Prices and stock are verified by the server. This checkout uses a simulated payment; no real payment details or charges are involved.</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <section className="order-detail-card"><div className="order-items">{cart.items.map((item) => <article className="order-line" key={item.itemId}><div><strong>{item.name}</strong><small>{item.sku} · {item.quantity} × {money(item.unitPrice, item.currency)}</small>{(!item.productAvailable || item.availableQuantity < item.quantity) && <small className="availability-warning">Availability will be verified at checkout.</small>}</div><strong>{money(item.lineTotal, item.currency)}</strong></article>)}</div>
      <div className="checkout-total"><strong>Current estimated total</strong><strong>{money(cart.subtotal, cart.currency)}</strong></div><p className="field-hint">The amount is calculated from the current catalog at checkout. Taxes, discounts, and shipping are currently zero.</p><button onClick={() => void submit()} disabled={pending}>{pending ? 'Processing simulated payment…' : `Place order · ${money(cart.subtotal, cart.currency)}`}</button>
    </section>
  </div>
}

export function AdminOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [summary, setSummary] = useState<OrderSummary | null>(null)
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [query, setQuery] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState('')
  const load = useCallback(async () => {
    try {
      const [page, counts] = await Promise.all([getAdminOrders({ status: status || undefined, q: query || undefined }), getAdminOrderSummary()])
      setOrders(page.content); setSummary(counts); setError('')
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Order management could not be loaded.') }
  }, [query, status])
  useEffect(() => { void Promise.resolve().then(load) }, [load])
  async function transition(order: Order, target: OrderStatus) {
    setPending(order.id); setError('')
    const reason = target === 'CANCELLED' ? window.prompt('Cancellation reason (required):') : undefined
    if (target === 'CANCELLED' && !reason?.trim()) { setPending(''); return }
    try { await updateOrderStatus(order.id, target, reason?.trim()); await load() }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Order status update failed.') }
    finally { setPending('') }
  }
  return <div className="page-content orders-page"><span className="eyebrow">ADMINISTRATION</span><h1>Order management</h1><p className="page-lead">Review orders and advance fulfillment through controlled state transitions.</p>
    {summary && <section className="order-summary-grid" aria-label="Order totals"><article><strong>{summary.totalOrders}</strong><span>Total orders</span></article>{statuses.map((value) => <article key={value}><strong>{summary.byStatus[value] ?? 0}</strong><span>{pretty(value)}</span></article>)}</section>}
    <div className="order-filters"><label>Search order number or customer email<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search orders" /></label><label>Filter status<select value={status} onChange={(event) => setStatus(event.target.value as OrderStatus | '')}><option value="">All statuses</option>{statuses.map((value) => <option key={value} value={value}>{pretty(value)}</option>)}</select></label></div>
    {error && <p className="form-error" role="alert">{error} <button className="text-button" onClick={() => void load()}>Retry</button></p>}
    {!orders.length ? <p className="empty-state">No orders match these filters.</p> : <section className="order-list" aria-label="Orders">{orders.map((order) => <article className="order-card" key={order.id}><div className="order-card-heading"><div><span className="eyebrow">{order.orderNumber}</span><h2>{order.items.map((item) => item.productName).join(', ')}</h2><small>{new Date(order.createdAt).toLocaleString()}</small></div><span className="order-status">{pretty(order.status)}</span></div><div className="order-card-footer"><span>{money(order.totalAmount, order.currency)} · {pretty(order.paymentStatus)}</span><div className="order-actions"><Link to={`/admin/orders/${order.id}`}>Details</Link>{order.status === 'CONFIRMED' && <button disabled={Boolean(pending)} onClick={() => void transition(order, 'PROCESSING')}>Start processing</button>}{order.status === 'PROCESSING' && <button disabled={Boolean(pending)} onClick={() => void transition(order, 'SHIPPED')}>Mark shipped</button>}{order.status === 'SHIPPED' && <button disabled={Boolean(pending)} onClick={() => void transition(order, 'DELIVERED')}>Mark delivered</button>}{order.status === 'CONFIRMED' && <button className="text-button" disabled={Boolean(pending)} onClick={() => void transition(order, 'CANCELLED')}>Cancel</button>}</div></div></article>)}</section>}
  </div>
}

export function AdminOrderDetailPage() {
  const { id = '' } = useParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => { try { setOrder(await getAdminOrder(id)); setError('') } catch (failure) { setError(failure instanceof Error ? failure.message : 'Order unavailable.') } }, [id])
  useEffect(() => { void Promise.resolve().then(load) }, [load])
  return <div className="page-content orders-page"><Link className="back-link" to="/admin/orders">← Order management</Link>{error && <p role="alert">{error}</p>}{order ? <><h1>{order.orderNumber}</h1><p>{pretty(order.status)} · {money(order.totalAmount, order.currency)}</p><OrderItems order={order} /><section className="order-timeline"><h2>Status history</h2>{order.history.map((event) => <p key={event.eventId}>{pretty(event.toStatus)} · {new Date(event.createdAt).toLocaleString()}</p>)}</section></> : !error && <p role="status">Loading order…</p>}</div>
}
