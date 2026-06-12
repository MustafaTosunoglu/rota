import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { LoginForm } from '@/components/auth/login-form'
import { useAuthStore } from '@/stores/auth'

function renderForm(onSuccess = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <LoginForm onSuccess={onSuccess} />
    </QueryClientProvider>,
  )
  return onSuccess
}

describe('LoginForm', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    useAuthStore.getState().clearSession()
  })

  it('shows validation errors and does not call the API for invalid input', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    renderForm()

    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument()
    expect(screen.getByText('This field is required.')).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('stores the session and calls onSuccess after a successful login', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ accessToken: 'jwt', refreshToken: 'rt', tokenType: 'Bearer', expiresIn: 900 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    const onSuccess = renderForm()

    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'sup3r-secret-pw')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(onSuccess).toHaveBeenCalled())
    expect(useAuthStore.getState().accessToken).toBe('jwt')
    expect(useAuthStore.getState().refreshToken).toBe('rt')
  })

  it('surfaces a credentials error on 401 without storing a session', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ title: 'Unauthorized', status: 401 }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )
    const onSuccess = renderForm()

    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password!')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Email or password is incorrect.')).toBeInTheDocument()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})
