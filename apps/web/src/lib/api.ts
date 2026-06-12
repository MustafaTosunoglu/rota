import { configureApiClient, getRefreshUrl, type TokenResponse } from '@rota/api-client'

import { useAuthStore } from '@/stores/auth'

/**
 * Exchanges the stored refresh token for a fresh access token (rotation: a new refresh token
 * comes back too). Uses plain fetch — NOT the generated client — so a 401 here can never
 * recurse into the mutator's own refresh-and-retry logic.
 */
export async function refreshSession(): Promise<boolean> {
  const refreshToken = useAuthStore.getState().refreshToken
  if (!refreshToken) {
    return false
  }
  const response = await fetch(getRefreshUrl(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    return false
  }
  const tokens = (await response.json()) as TokenResponse
  useAuthStore.getState().setSession(tokens)
  return true
}

/** Call once at startup: connects the generated API client to the auth store. */
export function setupApiClient() {
  configureApiClient({
    getAccessToken: () => useAuthStore.getState().accessToken,
    refreshSession,
    onSessionExpired: () => {
      useAuthStore.getState().clearSession()
      // Route guards react to the cleared store; a hard redirect would lose router state.
    },
  })
}
