import { useEffect } from 'react'
import { Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useMe } from '@rota/api-client'

import { Logo } from '@/components/logo'
import { ThemeToggle } from '@/components/theme-toggle'
import { UserMenu } from '@/components/layout/user-menu'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/auth'

/** Authenticated app frame: top navigation + content area. Used by every /app/* route. */
export function AppShell({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation()
  const me = useMe()
  const setUser = useAuthStore((s) => s.setUser)
  const pathname = useRouterState({ select: (s) => s.location.pathname })

  // Keep the persisted identity fresh for the user menu and dashboard.
  useEffect(() => {
    if (me.data) {
      setUser(me.data)
    }
  }, [me.data, setUser])

  const navItems = [
    { to: '/app/dashboard', label: t('nav.dashboard') },
    { to: '/app/documents', label: t('nav.documents') },
  ]

  return (
    <div className="flex min-h-svh flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
        <div className="flex h-14 items-center gap-6 px-4 md:px-6">
          <Link to="/app/dashboard" className="shrink-0">
            <Logo />
          </Link>
          <nav className="flex items-center gap-1">
            {navItems.map((item) => {
              const active = pathname.startsWith(item.to);
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                    active
                      ? 'bg-secondary text-secondary-foreground'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  )
}
