import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Upload } from 'lucide-react'
import {
  ApplyRequestDedupMode,
  getListCategoriesQueryKey,
  getListEndpointsQueryKey,
  getListEnvironmentsQueryKey,
  useApply,
  useParse,
  type ImportEndpoint,
  type ParsedImport,
} from '@rota/api-client'

import { MethodBadge } from '@/components/endpoints/method-badge'
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
import { Textarea } from '@/components/ui/textarea'
import { apiErrorMessage } from '@/lib/errors'

type Format = 'openapi' | 'postman' | 'curl'

export function ImportWizard({
  versionId,
  trigger,
}: {
  versionId: string
  trigger: React.ReactNode
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const parse = useParse()
  const apply = useApply()

  const [open, setOpen] = useState(false)
  const [format, setFormat] = useState<Format>('openapi')
  const [content, setContent] = useState('')
  const [parsed, setParsed] = useState<ParsedImport | null>(null)
  const [dedup, setDedup] = useState<ApplyRequestDedupMode>(ApplyRequestDedupMode.SKIP)

  const reset = () => {
    setContent('')
    setParsed(null)
    setFormat('openapi')
    setDedup(ApplyRequestDedupMode.SKIP)
  }

  const onFile = async (file: File | undefined) => {
    if (file) {
      setContent(await file.text())
    }
  }

  const doParse = async () => {
    try {
      const result = await parse.mutateAsync({ data: { format, content } })
      setParsed(result)
    } catch (error) {
      toast.error(apiErrorMessage(error, t('import.parseFailed')))
    }
  }

  const setEndpointCategory = (index: number, categoryName: string) => {
    if (!parsed?.endpoints) {
      return
    }
    const endpoints = parsed.endpoints.map((ep, i) =>
      i === index ? { ...ep, categoryName: categoryName || undefined } : ep,
    )
    setParsed({ ...parsed, endpoints })
  }

  const doImport = async () => {
    if (!parsed) {
      return
    }
    try {
      const result = await apply.mutateAsync({ versionId, data: { parsed, dedupMode: dedup } })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: getListEndpointsQueryKey(versionId) }),
        queryClient.invalidateQueries({ queryKey: getListCategoriesQueryKey(versionId) }),
        queryClient.invalidateQueries({ queryKey: getListEnvironmentsQueryKey(versionId) }),
      ])
      toast.success(
        t('import.done', {
          created: result.created ?? 0,
          overwritten: result.overwritten ?? 0,
          skipped: result.skipped ?? 0,
        }),
      )
      setOpen(false)
      reset()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('import.failed')))
    }
  }

  const endpoints = parsed?.endpoints ?? []

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) reset()
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{parsed ? t('import.previewTitle') : t('import.title')}</DialogTitle>
        </DialogHeader>

        {!parsed ? (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('import.format')}</Label>
              <Select value={format} onValueChange={(v) => setFormat(v as Format)}>
                <SelectTrigger className="max-w-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="openapi">{t('import.formatOpenapi')}</SelectItem>
                  <SelectItem value="postman">{t('import.formatPostman')}</SelectItem>
                  <SelectItem value="curl">{t('import.formatCurl')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="import-file" className="cursor-pointer">
                <Upload className="size-4" />
                {t('import.uploadFile')}
              </Label>
              <input
                id="import-file"
                type="file"
                accept=".json,.yaml,.yml,.txt"
                className="block text-sm text-muted-foreground file:mr-3 file:rounded-md file:border file:bg-secondary file:px-3 file:py-1.5 file:text-sm"
                onChange={(e) => void onFile(e.target.files?.[0])}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="import-content">{t('import.orPaste')}</Label>
              <Textarea
                id="import-content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder={t('import.pastePlaceholder')}
                className="min-h-48 font-mono text-xs"
              />
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>
                {t('common2.cancel')}
              </Button>
              <Button onClick={() => void doParse()} disabled={!content.trim() || parse.isPending}>
                {parse.isPending ? t('import.parsing') : t('import.parse')}
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              {parsed.suggestedTitle
                ? t('import.detected', { count: endpoints.length, title: parsed.suggestedTitle })
                : t('import.detectedNoTitle', { count: endpoints.length })}
            </p>

            <div className="max-h-72 space-y-1.5 overflow-y-auto rounded-lg border p-2">
              {endpoints.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted-foreground">{t('import.empty')}</p>
              ) : (
                endpoints.map((ep: ImportEndpoint, index: number) => (
                  <div key={index} className="flex items-center gap-2">
                    <MethodBadge method={ep.method ?? 'GET'} />
                    <span className="flex-1 truncate font-mono text-xs">{ep.path}</span>
                    <Input
                      value={ep.categoryName ?? ''}
                      onChange={(e) => setEndpointCategory(index, e.target.value)}
                      placeholder={t('import.uncategorized')}
                      className="h-7 w-40 text-xs"
                      aria-label={t('import.category')}
                    />
                  </div>
                ))
              )}
            </div>

            <div className="space-y-2">
              <Label>{t('import.dedup')}</Label>
              <Select value={dedup} onValueChange={(v) => setDedup(v as ApplyRequestDedupMode)}>
                <SelectTrigger className="max-w-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ApplyRequestDedupMode.SKIP}>{t('import.dedupSkip')}</SelectItem>
                  <SelectItem value={ApplyRequestDedupMode.OVERWRITE}>{t('import.dedupOverwrite')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={() => setParsed(null)}>
                {t('import.back')}
              </Button>
              <Button onClick={() => void doImport()} disabled={endpoints.length === 0 || apply.isPending}>
                {apply.isPending ? t('import.importing') : t('import.confirm')}
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
