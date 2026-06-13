import { useNavigate } from '@tanstack/react-router'
import { useLogout as useLogoutMutation } from '@rota/api-client'

import { useAuthStore } from '@/stores/auth'

/** Revokes the session server-side (best effort) then clears local state and returns to login. */
export function useLogout() {
  const navigate = useNavigate()
  const logout = useLogoutMutation()

  return async () => {
    const { refreshToken, clearSession } = useAuthStore.getState()
    try {
      if (refreshToken) {
        await logout.mutateAsync({ data: { refreshToken } })
      }
    } catch {
      // Even if revocation fails (network/expired), clear locally and move on.
    } finally {
      clearSession()
      void navigate({ to: '/login' })
    }
  }
}
