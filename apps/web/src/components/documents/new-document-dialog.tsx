import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { getListDocumentsQueryKey, useCreateDocument } from '@rota/api-client'

import { FieldError } from '@/components/auth/field-error'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { apiErrorMessage } from '@/lib/errors'

const schema = z.object({
  name: z.string().min(1, 'validation.required').max(200),
  slug: z.string().max(200).optional(),
  description: z.string().max(5000).optional(),
  visibility: z.enum(['public', 'unlisted', 'private']),
})

type FormValues = z.infer<typeof schema>

export function NewDocumentDialog({ trigger }: { trigger: React.ReactNode }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const create = useCreateDocument()
  const [open, setOpen] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { visibility: 'private' },
  })

  const onSubmit = async (values: FormValues) => {
    try {
      const doc = await create.mutateAsync({
        data: {
          name: values.name,
          slug: values.slug || undefined,
          description: values.description || undefined,
          visibility: values.visibility,
        },
      })
      await queryClient.invalidateQueries({ queryKey: getListDocumentsQueryKey() })
      toast.success(t('documents.created'))
      setOpen(false)
      reset()
      void navigate({ to: '/app/documents/$docId', params: { docId: doc.id! } })
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed'), {
        duplicate: t('documents.slugTaken'),
      }))
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('documents.create')}</DialogTitle>
          <DialogDescription>{t('documents.emptyHint')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="doc-name">{t('documents.name')}</Label>
            <Input
              id="doc-name"
              placeholder={t('documents.namePlaceholder')}
              aria-invalid={!!errors.name}
              {...register('name')}
            />
            <FieldError message={errors.name && t(errors.name.message ?? '')} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="doc-slug">
              {t('documents.slug')}{' '}
              <span className="font-normal text-muted-foreground">({t('common2.optional')})</span>
            </Label>
            <Input id="doc-slug" {...register('slug')} />
            <p className="text-xs text-muted-foreground">{t('documents.slugHint')}</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="doc-description">
              {t('documents.description')}{' '}
              <span className="font-normal text-muted-foreground">({t('common2.optional')})</span>
            </Label>
            <Textarea id="doc-description" {...register('description')} />
          </div>
          <div className="space-y-2">
            <Label>{t('documents.visibility')}</Label>
            <Select
              value={watch('visibility')}
              onValueChange={(v) => setValue('visibility', v as FormValues['visibility'])}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="private">{t('documents.visibilityPrivate')}</SelectItem>
                <SelectItem value="unlisted">{t('documents.visibilityUnlisted')}</SelectItem>
                <SelectItem value="public">{t('documents.visibilityPublic')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {t('common2.cancel')}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {t('documents.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
