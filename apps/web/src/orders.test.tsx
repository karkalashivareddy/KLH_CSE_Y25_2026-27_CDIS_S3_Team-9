import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { OrderHistoryPage } from './orders'

const order = {
  id: 'order-1', orderNumber: 'CR-ABC123', status: 'CONFIRMED', paymentStatus: 'SUCCEEDED',
  subtotal: 24.7, discountAmount: 0, taxAmount: 0, shippingAmount: 0, totalAmount: 24.7, currency: 'USD',
  createdAt: '2026-11-13T00:00:00Z', updatedAt: '2026-11-13T00:00:00Z',
  items: [{ itemId: 'line-1', productId: 'product-1', sku: 'LAMP-1', productName: 'Desk lamp', unitPrice: 12.35, quantity: 2, lineTotal: 24.7, createdAt: '2026-11-13T00:00:00Z' }],
  history: [],
}

describe('customer order history', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('renders persisted order snapshots and links to order detail', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [order], page: 0, size: 20, totalElements: 1, totalPages: 1 }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    render(<MemoryRouter initialEntries={['/orders']}><Routes><Route path="/orders" element={<OrderHistoryPage />} /></Routes></MemoryRouter>)

    expect(await screen.findByText('Desk lamp')).toBeTruthy()
    expect(screen.getByText('CR-ABC123')).toBeTruthy()
    expect(screen.getByRole('link', { name: /view order/i }).getAttribute('href')).toBe('/orders/order-1')
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/orders?page=0&size=20', expect.objectContaining({ credentials: 'same-origin' }))
  })
})
