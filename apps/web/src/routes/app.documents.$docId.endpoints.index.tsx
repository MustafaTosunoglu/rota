import { Link, createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import {
  useListCategories,
  useListEndpoints,
  type CategoryResponse,
  type EndpointSummaryResponse,
} from '@rota/api-client'

import { ExportButton } from '@/components/endpoints/export-button'
import { ImportWizard } from '@/components/endpoints/import-wizard'
import { NewEndpointDialog } from '@/components/endpoints/new-endpoint-dialog'
import { MethodBadge } from '@/components/endpoints/method-badge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useWorkspace } from '@/lib/workspace'
import { Upload } from 'lucide-react'

export const Route = createFileRoute('/app/documents/$docId/endpoints/')({
  component: EndpointsPage,
})

function EndpointsPage() {
  const { t } = useTranslation()
  const { documentId, document, editingVersion, editable } = useWorkspace()
  const versionId = editingVersion.id!
  const endpoints = useListEndpoints(versionId)
  const categories = useListCategories(versionId)

  const grouped = groupByCategory(endpoints.data ?? [], categories.data ?? [], t('categories.uncategorized'))

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-semibold tracking-tight">{t('endpoints.title')}</h1>
        <div className="flex items-center gap-2">
          <ExportButton versionId={versionId} fileBase={document.name ?? 'api'} />
          {editable && (
            <ImportWizard
              versionId={versionId}
              trigger={
                <Button variant="outline" size="sm">
                  <Upload />
                  {t('import.button')}
                </Button>
              }
            />
          )}
          {editable && (
            <NewEndpointDialog
              documentId={documentId}
              versionId={versionId}
              categories={categories.data ?? []}
              trigger={
                <Button>
                  <Plus />
                  {t('endpoints.new')}
                </Button>
              }
            />
          )}
        </div>
      </div>

      {endpoints.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : (endpoints.data?.length ?? 0) === 0 ? (
        <Card className="flex flex-col items-center gap-2 py-14 text-center">
          <p className="font-medium">{t('endpoints.empty')}</p>
          <p className="text-sm text-muted-foreground">{t('endpoints.emptyHint')}</p>
        </Card>
      ) : (
        <div className="space-y-6">
          {grouped.map((group) => (
            <div key={group.id} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{group.name}</h2>
              <div className="space-y-2">
                {group.endpoints.map((ep) => (
                  <Link
                    key={ep.id}
                    to="/app/documents/$docId/endpoints/$endpointId"
                    params={{ docId: documentId, endpointId: ep.id! }}
                    className="group"
                  >
                    <Card className="flex-row items-center gap-3 px-4 py-3 transition-colors group-hover:border-primary/40">
                      <MethodBadge method={ep.method ?? 'GET'} />
                      <span className="truncate font-mono text-sm">{ep.path}</span>
                      {ep.summary && (
                        <span className="truncate text-sm text-muted-foreground">— {ep.summary}</span>
                      )}
                      {ep.deprecated && (
                        <Badge variant="secondary" className="ml-auto">
                          {t('endpoints.deprecated')}
                        </Badge>
                      )}
                    </Card>
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

interface Group {
  id: string
  name: string
  endpoints: EndpointSummaryResponse[]
}

/** Groups endpoints by category, preserving category sort order, uncategorized last. */
function groupByCategory(
  endpoints: EndpointSummaryResponse[],
  categories: CategoryResponse[],
  uncategorizedLabel: string,
): Group[] {
  const byId = new Map<string, Group>()
  const ordered = [...categories].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  for (const category of ordered) {
    byId.set(category.id!, { id: category.id!, name: category.name ?? '', endpoints: [] })
  }
  const uncategorized: Group = { id: '__none__', name: uncategorizedLabel, endpoints: [] }
  for (const endpoint of endpoints) {
    const group = endpoint.categoryId ? byId.get(endpoint.categoryId) : undefined
    ;(group ?? uncategorized).endpoints.push(endpoint)
  }
  const result = [...byId.values()].filter((g) => g.endpoints.length > 0)
  if (uncategorized.endpoints.length > 0) {
    result.push(uncategorized)
  }
  return result
}
