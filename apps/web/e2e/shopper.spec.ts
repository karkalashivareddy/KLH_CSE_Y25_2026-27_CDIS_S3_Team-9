import { expect, test, type Page } from '@playwright/test'

const password = 'E2E-only-Strong-Passphrase-2026!'

async function registerAndSignIn(page: Page) {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`
  await page.goto('/register')
  await page.getByLabel('First name').fill('E2E')
  await page.getByLabel('Last name').fill('Shopper')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByText('Account created. Sign in to continue.')).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('complementary').getByText(email)).toBeVisible()
  return email
}

test('customer can register, authenticate, browse the live catalog, and inspect their cart', async ({ page }) => {
  await registerAndSignIn(page)
  await page.goto('/catalog')
  await expect(page.getByRole('heading', { name: 'Explore products' })).toBeVisible()
  await expect(page.getByText('Live catalog data from the CloudResilience API.')).toBeVisible()
  await expect(page.getByText('Products could not be loaded. Please retry.')).toHaveCount(0)

  await page.getByRole('link', { name: /Cart/ }).click()
  await expect(page.getByRole('heading', { name: /^Cart/ })).toBeVisible()
  await expect(page.getByText('Your cart is empty')).toBeVisible()
})

test('customer can add a seeded product and complete simulated checkout', async ({ page }) => {
  test.skip(process.env.E2E_CHECKOUT !== '1', 'Set E2E_CHECKOUT=1 only in a disposable local Compose environment with an active, stocked product.')

  await registerAndSignIn(page)
  await page.goto('/catalog')
  await expect(page.getByRole('heading', { name: 'Explore products' })).toBeVisible()
  const addButton = page.getByRole('button', { name: 'Add to cart' }).first()
  await expect(addButton, 'Checkout requires at least one active, stocked catalog product').toBeVisible()
  await addButton.click()
  await expect(page.getByText('Added to cart.')).toBeVisible()
  await page.getByRole('link', { name: /Cart/ }).click()
  await page.getByRole('link', { name: 'Continue to checkout' }).click()
  await expect(page.getByRole('heading', { name: 'Review and place order' })).toBeVisible()
  await page.getByRole('button', { name: /Place order/ }).click()
  await expect(page).toHaveURL(/\/orders\/[0-9a-f-]+$/i)
  await expect(page.getByRole('heading', { name: 'Order details' })).toBeVisible()
  await expect(page.getByText('Payment succeeded', { exact: true })).toBeVisible()
})
