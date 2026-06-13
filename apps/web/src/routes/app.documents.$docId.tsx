import { Outlet, createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useGetDocument, useListVersions } from '@rota/api-client'

import { DocumentSidebar } from '@/components/documents/document-sidebar'
import { Skeleton } from '@/components/ui/skeleton'
import { WorkspaceProvider, type WorkspaceValue } from '@/lib/workspace'

export const Route = createFileRoute('/app/documents/$docId')({
  component: DocumentWorkspace,
})

function DocumentWorkspace() {
  const { t } = useTranslation()
  const { docId } = Route.useParams()
  const document = useGetDocument(docId)
  const versions = useListVersions(docId)

  if (document.isLoading || versions.isLoading) {
    return (
      <div className="flex">
        <div className="w-60 shrink-0 border-r p-4">
          <Skeleton className="h-8 w-full" />
        </div>
        <div className="flex-1 p-6">
          <Skeleton className="h-40 w-full" />
        </div>
      </div>
    )
  }

  if (document.isError || !document.data || !versions.data) {
    return <div className="p-6 text-sm text-muted-foreground">{t('common.errors.unexpected')}</div>
  }

  // Editing targets the draft version; if everything is published/archived, fall back to the
  // most recent version (read-only) so the workspace still renders.
  const list = versions.data
  const draftVersion = list.find((v) => v.status === 'draft') ?? null
  const editingVersion =
    draftVersion ??
    [...list].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))[0]

  if (!editingVersion) {
    return <div className="p-6 text-sm text-muted-foreground">{t('common.errors.unexpected')}</div>
  }

  const value: WorkspaceValue = {
    documentId: docId,
    document: document.data,
    versions: list,
    draftVersion,
    editingVersion,
    editable: editingVersion.status === 'draft',
    refetch: () => {
      void document.refetch()
      void versions.refetch()
    },
  }

  return (
    <WorkspaceProvider value={value}>
      <div className="flex">
        <DocumentSidebar />
        <div className="min-w-0 flex-1">
          <Outlet />
        </div>
      </div>
    </WorkspaceProvider>
  )
}
