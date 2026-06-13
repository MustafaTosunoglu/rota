import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Plus, Trash2 } from 'lucide-react'
import {
  useCreateRequestBody,
  useDeleteRequestBody,
  useUpdateRequestBody,
  type RequestBodyResponse,
} from '@rota/api-client'

import { JsonCodeEditor } from '@/components/editor/json-code-editor'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { apiErrorMessage } from '@/lib/errors'
import { stringifyJson, parseJsonObject } from '@/lib/json'

export function RequestBodyEditor({
  endpointId,
  requestBodies,
  editable,
  onChanged,
}: {
  endpointId: string
  requestBodies: RequestBodyResponse[]
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const create = useCreateRequestBody()

  const add = async () => {
    try {
      await create.mutateAsync({ endpointId, data: { contentType: 'application/json' } })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed'), { conflict: t('endpoints.duplicate') }))
    }
  }

  return (
    <div className="space-y-3">
      {requestBodies.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t('endpoints.body.empty')}</p>
      ) : (
        requestBodies.map((body) => (
          <RequestBodyRow key={body.id} body={body} editable={editable} onChanged={onChanged} />
        ))
      )}
      {editable && (
        <Button variant="outline" size="sm" onClick={() => void add()} disabled={create.isPending}>
          <Plus />
          {t('endpoints.body.add')}
        </Button>
      )}
    </div>
  )
}

function RequestBodyRow({
  body,
  editable,
  onChanged,
}: {
  body: RequestBodyResponse
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const update = useUpdateRequestBody()
  const remove = useDeleteRequestBody()

  const [contentType, setContentType] = useState(body.contentType ?? 'application/json')
  const [schema, setSchema] = useState(stringifyJson(body.schemaJson))
  const [example, setExample] = useState(stringifyJson(body.exampleJson))

  const save = async () => {
    try {
      await update.mutateAsync({
        requestBodyId: body.id!,
        data: {
          contentType,
          schemaJson: parseJsonObject(schema),
          exampleJson: parseJsonObject(example),
        },
      })
      await onChanged()
      toast.success(t('endpoints.body.saved'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed'), { conflict: t('endpoints.duplicate') }))
    }
  }

  const del = async () => {
    try {
      await remove.mutateAsync({ requestBodyId: body.id! })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.deleteFailed')))
    }
  }

  return (
    <Card>
      <CardContent className="space-y-3 py-4">
        <div className="flex items-end gap-2">
          <div className="flex-1 space-y-1.5">
            <Label>{t('endpoints.body.contentType')}</Label>
            <Input value={contentType} disabled={!editable} onChange={(e) => setContentType(e.target.value)} />
          </div>
          {editable && (
            <Button size="icon" variant="ghost" onClick={() => void del()} aria-label={t('common2.delete')}>
              <Trash2 className="size-4 text-destructive" />
            </Button>
          )}
        </div>
        <div className="space-y-1.5">
          <Label>{t('endpoints.body.schema')}</Label>
          <JsonCodeEditor value={schema} editable={editable} onValidChange={setSchema} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('endpoints.body.example')}</Label>
          <JsonCodeEditor value={example} editable={editable} onValidChange={setExample} />
        </div>
        {editable && (
          <div className="flex justify-end">
            <Button size="sm" onClick={() => void save()} disabled={update.isPending}>
              {t('common2.save')}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
