import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { addCartItem, clearCart as clearApiCart, getCart, removeCartItem, updateCartItem, type Cart } from './api'
import { useAuth } from './auth-context'

type CartContextValue = { cart: Cart | null; loading: boolean; error: string; refresh: () => Promise<void>; add: (productId: string, quantity?: number) => Promise<void>; update: (itemId: string, quantity: number) => Promise<void>; remove: (itemId: string) => Promise<void>; clear: () => Promise<void> }
const CartContext = createContext<CartContextValue | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const { user, loading: authLoading } = useAuth()
  const [cart, setCart] = useState<Cart | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const isCustomer = Boolean(user?.roles.includes('CUSTOMER'))
  const refresh = useCallback(async () => {
    if (!isCustomer) { setCart(null); setError(''); return }
    setLoading(true)
    try { setCart(await getCart()); setError('') }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Your cart could not be loaded.') }
    finally { setLoading(false) }
  }, [isCustomer])
  useEffect(() => { if (!authLoading) void Promise.resolve().then(refresh) }, [authLoading, refresh])
  useEffect(() => {
    const clear = () => { setCart(null); setError('') }
    window.addEventListener('cloudresilience:unauthorized', clear)
    window.addEventListener('cloudresilience:logout', clear)
    return () => { window.removeEventListener('cloudresilience:unauthorized', clear); window.removeEventListener('cloudresilience:logout', clear) }
  }, [])
  const value = useMemo<CartContextValue>(() => ({
    cart, loading, error, refresh,
    async add(productId, quantity = 1) { setCart(await addCartItem(productId, quantity)); setError('') },
    async update(itemId, quantity) { setCart(await updateCartItem(itemId, quantity)); setError('') },
    async remove(itemId) { setCart(await removeCartItem(itemId)); setError('') },
    async clear() { setCart(await clearApiCart()); setError('') },
  }), [cart, loading, error, refresh])
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCart() {
  const value = useContext(CartContext)
  if (!value) throw new Error('useCart must be used within CartProvider')
  return value
}

export function CartPage() {
  const { user } = useAuth()
  const { cart, loading, error, refresh, update, remove, clear } = useCart()
  const [actionError, setActionError] = useState('')
  const [pending, setPending] = useState('')
  async function run(key: string, action: () => Promise<void>) {
    setPending(key); setActionError('')
    try { await action() } catch (reason) { setActionError(reason instanceof Error ? reason.message : 'Cart update failed.') }
    finally { setPending('') }
  }
  if (!user?.roles.includes('CUSTOMER')) return <div className="page-content cart-page"><span className="eyebrow">YOUR CART</span><h1>Sign in to view your cart</h1><p>Your saved cart is tied to your customer account.</p><Link className="button-link" to="/login">Sign in</Link></div>
  if (loading && !cart) return <div className="page-content cart-page" role="status"><span className="eyebrow">YOUR CART</span><h1>Loading your cart…</h1><div className="cart-skeleton" /></div>
  if (error && !cart) return <div className="page-content cart-page"><span className="eyebrow">YOUR CART</span><h1>Cart unavailable</h1><p role="alert">{error}</p><button onClick={() => void refresh()}>Retry</button></div>
  const items = cart?.items ?? []
  return <div className="page-content cart-page">
    <span className="eyebrow">YOUR SHOPPING CART</span><h1>Cart <span className="cart-count">{cart?.totalQuantity ?? 0}</span></h1>
    {(error || actionError) && <p role="alert" className="form-error">{actionError || error}<button className="text-button" onClick={() => void refresh()}>Retry</button></p>}
    {items.length === 0 ? <section className="empty-state cart-empty"><span aria-hidden="true">🛍</span><h2>Your cart is empty</h2><p>Browse the catalog and add products when you find something you like.</p><Link className="button-link" to="/catalog">Explore products</Link></section> : <div className="cart-layout">
      <section className="cart-items" aria-label="Cart items">{items.map((item) => <article className="cart-line" key={item.itemId}>
        <div className="cart-product"><span className="cart-product-mark" aria-hidden="true">CR</span><div><h2>{item.name}</h2><small>{item.sku}</small>{!item.productAvailable && <p className="availability-warning" role="status">This product is no longer active. Remove it before checkout.</p>}<p className={item.availableQuantity < item.quantity ? 'availability-warning' : 'availability-good'}>{item.availableQuantity === 0 ? 'Currently unavailable' : `${item.availableQuantity} available`}{item.availableQuantity < item.quantity && item.availableQuantity > 0 ? ` · only ${item.availableQuantity} currently available` : ''}</p></div></div>
        <div className="cart-line-price"><strong>{item.currency} {Number(item.lineTotal).toFixed(2)}</strong><small>{item.currency} {Number(item.unitPrice).toFixed(2)} each</small></div>
        <label className="cart-quantity">Quantity<input aria-label={`Quantity for ${item.name}`} type="number" min="1" max="9999" value={item.quantity} disabled={pending === item.itemId} onChange={(event) => { const q = Number(event.target.value); if (Number.isInteger(q) && q >= 1 && q <= 9999) void run(item.itemId, () => update(item.itemId, q)) }} /></label>
        <button className="text-button remove-item" disabled={pending === item.itemId} onClick={() => void run(item.itemId, () => remove(item.itemId))}>Remove</button>
      </article>)}</section>
      <aside className="cart-summary"><h2>Order summary</h2><div><span>Items</span><span>{cart?.itemCount ?? 0} lines · {cart?.totalQuantity ?? 0} total</span></div><div className="cart-subtotal"><strong>Current subtotal</strong><strong>{cart?.currency} {Number(cart?.subtotal ?? 0).toFixed(2)}</strong></div><p>Prices use the current catalog price. Availability and price will be checked again at checkout. Adding items does not reserve stock.</p><Link className="button-link checkout-link" to="/checkout">Continue to checkout</Link><button className="text-button clear-cart" disabled={Boolean(pending)} onClick={() => { if (window.confirm('Remove every item from your cart?')) void run('clear', clear) }}>Clear cart</button></aside>
    </div>}
  </div>
}
