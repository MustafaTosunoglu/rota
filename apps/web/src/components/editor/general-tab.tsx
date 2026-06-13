import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Braces } from 'lucide-react'
import type { CategoryResponse, EndpointDetailResponse } from '@rota/api-client'
import { useUpdateEndpoint } from '@rota/api-client'

import { MarkdownEditor } from '@/components/editor/markdown-editor'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { apiErrorMessage } from '@/lib/errors'

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const
const AUTH_TYPES = ['none', 'bearer', 'api_key', 'basic', 'oauth2'] as const
const UNCATEGORIZED = '__none__'

export function GeneralTab({
  endpoint,
  categories,
  editable,
  onChanged,
}: {
  endpoint: EndpointDetailResponse
  categories: CategoryResponse[]
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const update = useUpdateEndpoint()
  const pathRef = useRef<HTMLInputElement>(null)
  const [path, setPath] = useState(endpoint.path ?? '')

  const commit = async (data: Record<string, unknown>) => {
    try {
      await update.mutateAsync({ endpointId: endpoint.id!, data })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed'), {
        duplicate_operation: t('endpoints.duplicate'),
      }))
    }
  }

  // 3.8: insert {} at the cursor in the path field and place the caret between the braces.
  const insertPathVar = () => {
    const input = pathRef.current
    if (!input) {
      return
    }
    const start = input.selectionStart ?? path.length
    const end = input.selectionEnd ?? path.length
    const next = path.slice(0, start) + '{}' + path.slice(end)
    setPath(next)
    requestAnimationFrame(() => {
      input.focus()
      input.setSelectionRange(start + 1, start + 1)
    })
  }

  return (
    <div className="space-y-5">
      <div className="flex gap-2">
        <div className="w-32 space-y-1.5">
          <Label>{t('endpoints.method')}</Label>
          <Select value={endpoint.method} disabled={!editable} onValueChange={(v) => void commit({ method: v })}>
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
        <div className="flex-1 space-y-1.5">
          <Label htmlFor="ep-path">{t('endpoints.path')}</Label>
          <div className="flex gap-1">
            <Input
              id="ep-path"
              ref={pathRef}
              value={path}
              disabled={!editable}
              onChange={(e) => setPath(e.target.value)}
              onBlur={() => path !== endpoint.path && void commit({ path })}
              className="font-mono"
            />
            {editable && (
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
            )}
          </div>
        </div>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="ep-summary">{t('endpoints.general.summary')}</Label>
        <Input
          id="ep-summary"
          defaultValue={endpoint.summary ?? ''}
          disabled={!editable}
          onBlur={(e) => e.target.value !== (endpoint.summary ?? '') && void commit({ summary: e.target.value })}
        />
      </div>

      <div className="flex gap-2">
        <div className="flex-1 space-y-1.5">
          <Label>{t('endpoints.category')}</Label>
          <Select
            value={endpoint.categoryId ?? UNCATEGORIZED}
            disabled={!editable}
            onValueChange={(v) =>
              void commit(v === UNCATEGORIZED ? { clearCategory: true } : { categoryId: v })
            }
          >
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
        <div className="flex-1 space-y-1.5">
          <Label>{t('endpoints.general.authType')}</Label>
          <Select value={endpoint.authType} disabled={!editable} onValueChange={(v) => void commit({ authType: v })}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AUTH_TYPES.map((a) => (
                <SelectItem key={a} value={a}>
                  {a}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Switch
          checked={endpoint.deprecated}
          disabled={!editable}
          onCheckedChange={(checked) => void commit({ deprecated: checked })}
        />
        <Label>{t('endpoints.general.deprecated')}</Label>
      </div>

      <div className="space-y-1.5">
        <Label>{t('endpoints.general.description')}</Label>
        <MarkdownEditor
          value={endpoint.descriptionMd ?? ''}
          editable={editable}
          onCommit={(md) => md !== (endpoint.descriptionMd ?? '') && void commit({ descriptionMd: md })}
        />
      </div>
    </div>
  )
}
