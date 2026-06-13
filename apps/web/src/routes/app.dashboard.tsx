import { Link, createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, FileText, Plus } from 'lucide-react'
import { useListDocuments } from '@rota/api-client'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuthStore } from '@/stores/auth'

export const Route = createFileRoute('/app/dashboard')({
  component: DashboardPage,
})

function DashboardPage() {
  const { t } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const documents = useListDocuments()

  const count = documents.data?.length ?? 0

  return (
    <div className="mx-auto w-full max-w-5xl space-y-8 p-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          {user?.displayName ? t('dashboard.welcome', { name: user.displayName }) : t('dashboard.title')}
        </h1>
        <p className="text-sm text-muted-foreground">{user?.email}</p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <Card className="md:col-span-2">
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <div className="space-y-1">
              <CardTitle>{t('dashboard.documentsTitle')}</CardTitle>
              <CardDescription>
                {documents.isLoading ? t('common2.loading') : t('dashboard.documentCount', { count })}
              </CardDescription>
            </div>
            <Button asChild size="sm">
              <Link to="/app/documents">
                {t('documents.new')}
                <Plus />
              </Link>
            </Button>
          </CardHeader>
          <CardContent>
            {documents.isLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : count === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                {t('dashboard.noDocuments')}
              </p>
            ) : (
              <ul className="divide-y">
                {documents.data!.slice(0, 5).map((doc) => (
                  <li key={doc.id}>
                    <Link
                      to="/app/documents/$docId"
                      params={{ docId: doc.id! }}
                      className="flex items-center gap-3 py-2.5 text-sm hover:text-primary"
                    >
                      <FileText className="size-4 text-muted-foreground" />
                      <span className="flex-1 truncate font-medium">{doc.name}</span>
                      <ArrowRight className="size-4 text-muted-foreground" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.recentActivity')}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="py-6 text-center text-sm text-muted-foreground">
              {t('dashboard.activityPlaceholder')}
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
