import { useEffect } from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { LogOut } from 'lucide-react'
import { useLogout, useMe } from '@rota/api-client'

import { Logo } from '@/components/logo'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuthStore } from '@/stores/auth'

export const Route = createFileRoute('/app/dashboard')({
  component: DashboardPage,
})

function DashboardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const me = useMe()
  const logout = useLogout()
  const { refreshToken, clearSession, setUser, user } = useAuthStore()

  // Keep the persisted identity in sync with the server.
  useEffect(() => {
    if (me.data) {
      setUser(me.data)
    }
  }, [me.data, setUser])

  const onLogout = async () => {
    try {
      if (refreshToken) {
        // Revokes the refresh token and blacklists the current access token server-side.
        await logout.mutateAsync({ data: { refreshToken } })
      }
    } finally {
      clearSession()
      void navigate({ to: '/login' })
    }
  }

  const identity = me.data ?? user

  return (
    <div className="flex min-h-svh flex-col">
      <header className="flex items-center justify-between border-b px-6 py-3">
        <Logo />
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <Button variant="outline" size="sm" onClick={() => void onLogout()}>
            <LogOut />
            {t('auth.logout')}
          </Button>
        </div>
      </header>
      <main className="mx-auto w-full max-w-3xl flex-1 space-y-6 p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          {identity?.displayName
            ? t('dashboard.welcome', { name: identity.displayName })
            : t('dashboard.title')}
        </h1>
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.signedInAs')}</CardTitle>
            <CardDescription>{identity?.email ?? t('common.loading')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-1 text-sm text-muted-foreground">
            <p>
              {t('dashboard.tenant')}: <span className="font-mono">{identity?.tenantId ?? '—'}</span>
            </p>
            <p>
              {t('dashboard.roles')}: {identity?.roles?.join(', ') ?? '—'}
            </p>
          </CardContent>
        </Card>
        <p className="text-sm text-muted-foreground">{t('dashboard.placeholder')}</p>
      </main>
    </div>
  )
}
