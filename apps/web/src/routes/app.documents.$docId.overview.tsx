import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  getGetDocumentQueryKey,
  getListDocumentsQueryKey,
  getListVersionsQueryKey,
  useCreateVersion,
  usePublishVersion,
  useUpdateDocument,
} from '@rota/api-client'

import { EnvironmentsCard } from '@/components/documents/environments-card'
import { FieldError } from '@/components/auth/field-error'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { apiErrorMessage } from '@/lib/errors'
import { useWorkspace } from '@/lib/workspace'

export const Route = createFileRoute('/app/documents/$docId/overview')({
  component: OverviewPage,
})

const schema = z.object({
  name: z.string().min(1, 'validation.required').max(200),
  slug: z.string().min(1, 'validation.required').max(200),
  description: z.string().max(5000).optional(),
  visibility: z.enum(['public', 'unlisted', 'private']),
})

type FormValues = z.infer<typeof schema>

function OverviewPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { documentId, document, draftVersion, editingVersion, refetch } = useWorkspace()
  const update = useUpdateDocument()

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      name: document.name ?? '',
      slug: document.slug ?? '',
      description: document.description ?? '',
      visibility: (document.visibility as FormValues['visibility']) ?? 'private',
    },
  })

  const onSubmit = async (values: FormValues) => {
    try {
      await update.mutateAsync({ documentId, data: values })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: getGetDocumentQueryKey(documentId) }),
        queryClient.invalidateQueries({ queryKey: getListDocumentsQueryKey() }),
      ])
      toast.success(t('documents.updated'))
      reset(values)
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed'), {
        duplicate: t('documents.slugTaken'),
      }))
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6 p-6">
      <h1 className="text-xl font-semibold tracking-tight">{t('overview.title')}</h1>

      <Card>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-6" noValidate>
          <CardHeader>
            <CardTitle>{t('overview.general')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">{t('documents.name')}</Label>
              <Input id="name" aria-invalid={!!errors.name} {...register('name')} />
              <FieldError message={errors.name && t(errors.name.message ?? '')} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="slug">{t('documents.slug')}</Label>
              <Input id="slug" aria-invalid={!!errors.slug} {...register('slug')} />
              <FieldError message={errors.slug && t(errors.slug.message ?? '')} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t('documents.description')}</Label>
              <Textarea id="description" {...register('description')} />
            </div>
            <div className="space-y-2">
              <Label>{t('documents.visibility')}</Label>
              <Select
                value={watch('visibility')}
                onValueChange={(v) =>
                  setValue('visibility', v as FormValues['visibility'], { shouldDirty: true })
                }
              >
                <SelectTrigger className="max-w-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="private">{t('documents.visibilityPrivate')}</SelectItem>
                  <SelectItem value="unlisted">{t('documents.visibilityUnlisted')}</SelectItem>
                  <SelectItem value="public">{t('documents.visibilityPublic')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex justify-end">
              <Button type="submit" disabled={isSubmitting || !isDirty}>
                {t('common2.save')}
              </Button>
            </div>
          </CardContent>
        </form>
      </Card>

      <PublishCard
        documentId={documentId}
        draftVersionId={draftVersion?.id ?? null}
        editingVersionId={editingVersion.id!}
        editingVersionLabel={editingVersion.versionLabel ?? ''}
        editingVersionStatus={editingVersion.status ?? ''}
        onChanged={() => {
          void queryClient.invalidateQueries({ queryKey: getListVersionsQueryKey(documentId) })
          void queryClient.invalidateQueries({ queryKey: getGetDocumentQueryKey(documentId) })
          refetch()
        }}
      />

      <EnvironmentsCard versionId={editingVersion.id!} editable={editingVersion.status === 'draft'} />

      <Card>
        <CardHeader>
          <CardTitle>{t('overview.branding')}</CardTitle>
          <CardDescription>{t('overview.brandingPlaceholder')}</CardDescription>
        </CardHeader>
      </Card>
    </div>
  )
}

function PublishCard({
  documentId,
  draftVersionId,
  editingVersionId,
  editingVersionLabel,
  editingVersionStatus,
  onChanged,
}: {
  documentId: string
  draftVersionId: string | null
  editingVersionId: string
  editingVersionLabel: string
  editingVersionStatus: string
  onChanged: () => void
}) {
  const { t } = useTranslation()
  const publish = usePublishVersion()
  const createVersion = useCreateVersion()

  const doPublish = async () => {
    if (!draftVersionId) {
      return
    }
    try {
      await publish.mutateAsync({ versionId: draftVersionId })
      onChanged()
      toast.success(t('overview.publishDone'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  const newDraftLabel = nextVersionLabel(editingVersionLabel)
  const createDraft = async () => {
    try {
      await createVersion.mutateAsync({
        documentId,
        data: { versionLabel: newDraftLabel, cloneFromVersionId: editingVersionId },
      })
      onChanged()
      toast.success(t('documents.updated'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{draftVersionId ? t('overview.draftVersion') : t('overview.currentVersion')}</CardTitle>
        <CardDescription>
          {editingVersionLabel} · {editingVersionStatus}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {draftVersionId ? (
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm text-muted-foreground">{t('overview.publishHint')}</p>
            <Button onClick={() => void doPublish()} disabled={publish.isPending}>
              {t('overview.publish')}
            </Button>
          </div>
        ) : (
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm text-muted-foreground">{t('overview.noDraft')}</p>
            <Button variant="outline" onClick={() => void createDraft()} disabled={createVersion.isPending}>
              {newDraftLabel}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** "v1" → "v2"; otherwise append "-draft" so a clone never collides on the version label. */
function nextVersionLabel(current: string): string {
  const match = /^v(\d+)$/.exec(current.trim())
  if (match) {
    return `v${Number(match[1]) + 1}`
  }
  return `${current || 'v1'}-draft`
}
