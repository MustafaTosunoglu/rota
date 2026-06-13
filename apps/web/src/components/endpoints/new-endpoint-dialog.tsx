import { useRef, useState } from 'react'
import { Braces } from 'lucide-react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  getListEndpointsQueryKey,
  useCreateEndpoint,
  type CategoryResponse,
} from '@rota/api-client'

import { FieldError } from '@/components/auth/field-error'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { apiErrorMessage } from '@/lib/errors'

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const
const UNCATEGORIZED = '__none__'

const schema = z.object({
  method: z.enum(METHODS),
  path: z.string().min(1, 'validation.required').max(2000),
  summary: z.string().max(500).optional(),
  categoryId: z.string(),
})

type FormValues = z.infer<typeof schema>

export function NewEndpointDialog({
  documentId,
  versionId,
  categories,
  trigger,
}: {
  documentId: string
  versionId: string
  categories: CategoryResponse[]
  trigger: React.ReactNode
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const create = useCreateEndpoint()
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
    defaultValues: { method: 'GET', categoryId: UNCATEGORIZED },
  })

  // Merge RHF's ref with a local one so the {} button can insert at the cursor (3.8).
  const pathInputRef = useRef<HTMLInputElement | null>(null)
  const { ref: registerPathRef, ...pathField } = register('path')

  const insertPathVar = () => {
    const input = pathInputRef.current
    if (!input) {
      return
    }
    const start = input.selectionStart ?? input.value.length
    const end = input.selectionEnd ?? start
    const next = input.value.slice(0, start) + '{}' + input.value.slice(end)
    setValue('path', next, { shouldDirty: true, shouldValidate: true })
    requestAnimationFrame(() => {
      input.focus()
      input.setSelectionRange(start + 1, start + 1)
    })
  }

  const onSubmit = async (values: FormValues) => {
    try {
      const endpoint = await create.mutateAsync({
        versionId,
        data: {
          method: values.method,
          path: values.path,
          summary: values.summary || undefined,
          categoryId: values.categoryId === UNCATEGORIZED ? undefined : values.categoryId,
        },
      })
      await queryClient.invalidateQueries({ queryKey: getListEndpointsQueryKey(versionId) })
      toast.success(t('endpoints.created'))
      setOpen(false)
      reset()
      void navigate({
        to: '/app/documents/$docId/endpoints/$endpointId',
        params: { docId: documentId, endpointId: endpoint.id! },
      })
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed'), {
        duplicate_operation: t('endpoints.duplicate'),
      }))
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('endpoints.new')}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="flex gap-2">
            <div className="w-32 space-y-2">
              <Label>{t('endpoints.method')}</Label>
              <Select value={watch('method')} onValueChange={(v) => setValue('method', v as FormValues['method'])}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {METHODS.map((m) => (
                    <SelectItem key={m} value={m}>
                      {m}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex-1 space-y-2">
              <Label htmlFor="ep-path">{t('endpoints.path')}</Label>
              <div className="flex gap-1">
                <Input
                  id="ep-path"
                  placeholder={t('endpoints.pathPlaceholder')}
                  aria-invalid={!!errors.path}
                  className="font-mono"
                  {...pathField}
                  ref={(el) => {
                    registerPathRef(el)
                    pathInputRef.current = el
                  }}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  onClick={insertPathVar}
                  title={t('endpoints.params.insertPathVar')}
                  aria-label={t('endpoints.params.insertPathVar')}
                >
                  <Braces className="size-4" />
                </Button>
              </div>
              <FieldError message={errors.path && t(errors.path.message ?? '')} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="ep-summary">
              {t('endpoints.summary')}{' '}
              <span className="font-normal text-muted-foreground">({t('common2.optional')})</span>
            </Label>
            <Input id="ep-summary" {...register('summary')} />
          </div>
          <div className="space-y-2">
            <Label>{t('endpoints.category')}</Label>
            <Select value={watch('categoryId')} onValueChange={(v) => setValue('categoryId', v)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={UNCATEGORIZED}>{t('categories.uncategorized')}</SelectItem>
                {categories.map((c) => (
                  <SelectItem key={c.id} value={c.id!}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {t('common2.cancel')}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {t('common2.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
