import { Link, createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, FileText, Plus } from 'lucide-react'
import { useListDocuments } from '@rota/api-client'

import { NewDocumentDialog } from '@/components/documents/new-document-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export const Route = createFileRoute('/app/documents/')({
  component: DocumentsPage,
})

function DocumentsPage() {
  const { t } = useTranslation()
  const documents = useListDocuments()

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">{t('documents.title')}</h1>
        <NewDocumentDialog
          trigger={
            <Button>
              <Plus />
              {t('documents.new')}
            </Button>
          }
        />
      </div>

      {documents.isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : (documents.data?.length ?? 0) === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-16 text-center">
          <FileText className="size-10 text-muted-foreground" />
          <div>
            <p className="font-medium">{t('documents.empty')}</p>
            <p className="text-sm text-muted-foreground">{t('documents.emptyHint')}</p>
          </div>
          <NewDocumentDialog
            trigger={
              <Button>
                <Plus />
                {t('documents.new')}
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="grid gap-3">
          {documents.data!.map((doc) => (
            <Link
              key={doc.id}
              to="/app/documents/$docId"
              params={{ docId: doc.id! }}
              className="group"
            >
              <Card className="flex-row items-center gap-4 px-5 py-4 transition-colors group-hover:border-primary/40">
                <FileText className="size-5 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">{doc.name}</span>
                    <Badge variant={doc.publishedAt ? 'default' : 'secondary'}>
                      {doc.publishedAt ? t('documents.publishedBadge') : t('documents.draftBadge')}
                    </Badge>
                  </div>
                  {doc.description && (
                    <p className="truncate text-sm text-muted-foreground">{doc.description}</p>
                  )}
                  <p className="font-mono text-xs text-muted-foreground">/{doc.slug}</p>
                </div>
                <ArrowRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
