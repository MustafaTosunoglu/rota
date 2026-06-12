import { beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '@/stores/auth'

describe('auth store', () => {
  beforeEach(() => {
    useAuthStore.getState().clearSession()
    localStorage.clear()
  })

  it('setSession stores both tokens, but only the refresh token is persisted', () => {
    useAuthStore.getState().setSession({
      accessToken: 'jwt-access',
      refreshToken: 'tenant.secret',
      tokenType: 'Bearer',
      expiresIn: 900,
    })

    expect(useAuthStore.getState().accessToken).toBe('jwt-access')
    expect(useAuthStore.getState().refreshToken).toBe('tenant.secret')

    const persisted = localStorage.getItem('rota-auth') ?? ''
    expect(persisted).toContain('tenant.secret')
    // The short-lived JWT must never be written to localStorage.
    expect(persisted).not.toContain('jwt-access')
  })

  it('clearSession wipes tokens and user', () => {
    useAuthStore.getState().setSession({ accessToken: 'a', refreshToken: 'r' })
    useAuthStore.getState().setUser({ email: 'a@b.c' })

    useAuthStore.getState().clearSession()

    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().refreshToken).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
  })
})
