import { Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, FolderTree, LayoutList, Settings2 } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

/** Secondary nav shown alongside every document workspace page. */
export function DocumentSidebar() {
  const { t } = useTranslation()
  const { document, documentId, editable } = useWorkspace()
  const pathname = useRouterState({ select: (s) => s.location.pathname })

  const items = [
    { to: '/app/documents/$docId/overview', label: t('overview.title'), icon: Settings2 },
    { to: '/app/documents/$docId/endpoints', label: t('endpoints.title'), icon: LayoutList },
    { to: '/app/documents/$docId/categories', label: t('categories.title'), icon: FolderTree },
  ] as const

  return (
    <aside className="w-60 shrink-0 border-r p-4">
      <Link
        to="/app/documents"
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" />
        {t('nav.backToDocuments')}
      </Link>
      <div className="mb-4">
        <h2 className="truncate font-semibold">{document.name}</h2>
        <div className="mt-1 flex items-center gap-2">
          <Badge variant={editable ? 'secondary' : 'default'}>
            {editable ? t('documents.draftBadge') : t('documents.publishedBadge')}
          </Badge>
        </div>
      </div>
      <nav className="flex flex-col gap-1">
        {items.map((item) => {
          const resolved = item.to.replace('$docId', documentId)
          const active = pathname.startsWith(resolved)
          const Icon = item.icon
          return (
            <Link
              key={item.to}
              to={item.to}
              params={{ docId: documentId }}
              className={cn(
                'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                active
                  ? 'bg-secondary text-secondary-foreground'
                  : 'text-muted-foreground hover:bg-accent hover:text-foreground',
              )}
            >
              <Icon className="size-4" />
              {item.label}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
