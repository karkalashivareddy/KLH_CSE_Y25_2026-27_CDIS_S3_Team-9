import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { Cart } from './api'
import { AuthContext } from './auth-context'
import { CartPage, CartProvider } from './cart'

const customer = { id: 'u-1', email: 'buyer@example.test', firstName: 'Test', lastName: 'Buyer', roles: ['CUSTOMER' as const] }
const cart: Cart = { cartId: 'c-1', itemCount: 1, totalQuantity: 2, subtotal: 25, currency: 'USD', items: [
  { itemId: 'i-1', productId: 'p-1', sku: 'ITEM-1', name: 'Desk lamp', status: 'ACTIVE', unitPrice: 12.5, currency: 'USD', quantity: 2, lineTotal: 25, availableQuantity: 1, productAvailable: true },
] }

function mountCart() {
  const auth = { user: customer, loading: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }
  return render(<AuthContext.Provider value={auth}><CartProvider><MemoryRouter initialEntries={['/cart']}><Routes><Route path="/cart" element={<CartPage/>}/></Routes></MemoryRouter></CartProvider></AuthContext.Provider>)
}

describe('customer cart page', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('loads real cart data, shows availability warning and current subtotal', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(cart), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    mountCart()
    expect(await screen.findByText('Desk lamp')).toBeTruthy()
    expect(screen.getAllByText('USD 25.00')).toHaveLength(2)
    expect(screen.getByText(/only 1 currently available/)).toBeTruthy()
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/cart')
  })

  it('updates a quantity through CSRF-protected API and refreshes displayed totals', async () => {
    const updated = { ...cart, totalQuantity: 4, subtotal: 50, items: [{ ...cart.items[0], quantity: 4, lineTotal: 50 }] }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(cart), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(updated), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    mountCart()
    const input = await screen.findByLabelText('Quantity for Desk lamp')
    fireEvent.change(input, { target: { value: '4' } })
    await waitFor(() => expect(screen.getAllByText('USD 50.00')).toHaveLength(2))
    expect(fetchMock.mock.calls[2][1]).toMatchObject({ method: 'PATCH', headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-token' }) })
  })

  it('clears cached cart data after a logout event', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(cart), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    mountCart()
    expect(await screen.findByText('Desk lamp')).toBeTruthy()
    window.dispatchEvent(new Event('cloudresilience:logout'))
    expect(await screen.findByText('Your cart is empty')).toBeTruthy()
  })

  it('offers a retry when the persisted cart endpoint is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'unavailable' }), { status: 503 })))
    mountCart()
    expect(await screen.findByText('Cart unavailable')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy()
  })
})
