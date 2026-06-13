import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Plus, Trash2 } from 'lucide-react'
import {
  useCreateResponse,
  useDeleteResponse,
  useUpdateResponse,
  type ResponseResponse,
} from '@rota/api-client'

import { JsonCodeEditor } from '@/components/editor/json-code-editor'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { apiErrorMessage } from '@/lib/errors'
import { parseJsonObject, stringifyJson } from '@/lib/json'

export function ResponseEditor({
  endpointId,
  responses,
  editable,
  onChanged,
}: {
  endpointId: string
  responses: ResponseResponse[]
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const create = useCreateResponse()

  const add = async () => {
    try {
      await create.mutateAsync({ endpointId, data: { statusCode: 200 } })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  return (
    <div className="space-y-3">
      {responses.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t('endpoints.responses.empty')}</p>
      ) : (
        responses.map((response) => (
          <ResponseRow key={response.id} response={response} editable={editable} onChanged={onChanged} />
        ))
      )}
      {editable && (
        <Button variant="outline" size="sm" onClick={() => void add()} disabled={create.isPending}>
          <Plus />
          {t('endpoints.responses.add')}
        </Button>
      )}
    </div>
  )
}

function ResponseRow({
  response,
  editable,
  onChanged,
}: {
  response: ResponseResponse
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const update = useUpdateResponse()
  const remove = useDeleteResponse()

  const [statusCode, setStatusCode] = useState(String(response.statusCode ?? 200))
  const [description, setDescription] = useState(response.description ?? '')
  const [contentType, setContentType] = useState(response.contentType ?? 'application/json')
  const [schema, setSchema] = useState(stringifyJson(response.schemaJson))
  const [example, setExample] = useState(stringifyJson(response.exampleJson))

  const save = async () => {
    const code = Number(statusCode)
    if (!Number.isInteger(code) || code < 100 || code > 599) {
      toast.error(t('endpoints.responses.statusCode') + ' 100–599')
      return
    }
    try {
      await update.mutateAsync({
        responseId: response.id!,
        data: {
          statusCode: code,
          description,
          contentType,
          schemaJson: parseJsonObject(schema),
          exampleJson: parseJsonObject(example),
        },
      })
      await onChanged()
      toast.success(t('endpoints.responses.saved'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  const del = async () => {
    try {
      await remove.mutateAsync({ responseId: response.id! })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.deleteFailed')))
    }
  }

  return (
    <Card>
      <CardContent className="space-y-3 py-4">
        <div className="flex items-end gap-2">
          <div className="w-28 space-y-1.5">
            <Label>{t('endpoints.responses.statusCode')}</Label>
            <Input
              type="number"
              value={statusCode}
              disabled={!editable}
              onChange={(e) => setStatusCode(e.target.value)}
            />
          </div>
          <div className="flex-1 space-y-1.5">
            <Label>{t('endpoints.responses.contentType')}</Label>
            <Input value={contentType} disabled={!editable} onChange={(e) => setContentType(e.target.value)} />
          </div>
          {editable && (
            <Button size="icon" variant="ghost" onClick={() => void del()} aria-label={t('common2.delete')}>
              <Trash2 className="size-4 text-destructive" />
            </Button>
          )}
        </div>
        <div className="space-y-1.5">
          <Label>{t('endpoints.responses.description')}</Label>
          <Input value={description} disabled={!editable} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('endpoints.responses.schema')}</Label>
          <JsonCodeEditor value={schema} editable={editable} onValidChange={setSchema} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('endpoints.responses.example')}</Label>
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
