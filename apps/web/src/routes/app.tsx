import { Outlet, createFileRoute, redirect } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/app-shell'
import { refreshSession } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'

/** Authenticated shell: every /app/* route requires a live session. */
export const Route = createFileRoute('/app')({
  beforeLoad: async () => {
    const { accessToken, refreshToken } = useAuthStore.getState()
    if (accessToken) {
      return
    }
    // Page reload: the access token lives only in memory — try the persisted refresh token.
    if (!refreshToken || !(await refreshSession())) {
      useAuthStore.getState().clearSession()
      throw redirect({ to: '/login' })
    }
  },
  component: () => (
    <AppShell>
      <Outlet />
    </AppShell>
  ),
})
