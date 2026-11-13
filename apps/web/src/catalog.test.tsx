import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { CatalogPage } from './catalog'
import { AuthContext } from './auth-context'
import { CartProvider } from './cart'

const page = { content: [
  { id: 'p-1', sku: 'LAMP-1', name: 'Desk lamp', slug: 'desk-lamp', price: 29.5, currency: 'USD', category_id: 'c-1', category_name: 'Lighting', available_quantity: 2 },
], page: 0, size: 12, totalElements: 13, totalPages: 2 }
const auth = { user: null, loading: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }
function renderCatalog() { return render(<AuthContext.Provider value={auth}><CartProvider><MemoryRouter><CatalogPage/></MemoryRouter></CartProvider></AuthContext.Provider>) }

describe('catalog page', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renders API products, category filters, availability and database pagination', async () => {
    const fetchMock=vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([{id:'c-1',name:'Lighting',slug:'lighting',active:true}]),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify(page),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify(page),{status:200}))
    vi.stubGlobal('fetch',fetchMock)
    renderCatalog()
    expect(await screen.findByRole('link',{name:'Desk lamp'})).toBeTruthy()
    expect(screen.getByText('2 available')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Search products'),{target:{value:'lamp'}})
    fireEvent.click(screen.getByRole('button',{name:'Apply filters'}))
    await waitFor(()=>expect(fetchMock).toHaveBeenCalledTimes(3))
    expect(String(fetchMock.mock.calls[2][0])).toContain('q=lamp')
    fireEvent.click(screen.getByRole('button',{name:'Next'}))
    await waitFor(()=>expect(fetchMock).toHaveBeenCalledTimes(4))
    expect(String(fetchMock.mock.calls[3][0])).toContain('page=1')
  })

  it('shows honest empty and API failure states', async () => {
    vi.stubGlobal('fetch',vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([]),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify({...page,content:[],totalElements:0,totalPages:0}),{status:200})))
    const {unmount}=renderCatalog()
    expect(await screen.findByText('No products found')).toBeTruthy()
    unmount()
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({message:'unavailable'}),{status:503})))
    renderCatalog()
    expect(await screen.findByRole('alert')).toBeTruthy()
  })
})
