import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { MeResponse, TokenResponse } from '@rota/api-client'

interface AuthState {
  /** Short-lived JWT — kept in memory only, never persisted. */
  accessToken: string | null
  /**
   * Opaque rotating refresh token. Persisted to localStorage so the session survives reloads
   * (1F decision); it is single-use, rotated on every refresh and revoked server-side on
   * logout / password reset.
   */
  refreshToken: string | null
  /** Last known identity, persisted for instant paint; re-fetched via /me when needed. */
  user: MeResponse | null
  setSession: (tokens: TokenResponse) => void
  setUser: (user: MeResponse | null) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (tokens) =>
        set({ accessToken: tokens.accessToken ?? null, refreshToken: tokens.refreshToken ?? null }),
      setUser: (user) => set({ user }),
      clearSession: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    {
      name: 'rota-auth',
      // accessToken deliberately excluded: it must die with the tab/page.
      partialize: (state) => ({ refreshToken: state.refreshToken, user: state.user }),
    },
  ),
)

export function isAuthenticated(): boolean {
  return useAuthStore.getState().refreshToken !== null
}
