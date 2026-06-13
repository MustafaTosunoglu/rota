import { useState } from 'react'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { GripVertical, Plus, Trash2 } from 'lucide-react'
import {
  useCreateParameter,
  useDeleteParameter,
  useUpdateParameter,
  type ParameterResponse,
} from '@rota/api-client'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { apiErrorMessage } from '@/lib/errors'
import { cn } from '@/lib/utils'

const LOCATIONS = ['path', 'query', 'header', 'cookie'] as const
const COMMON_HEADERS = ['Accept', 'Content-Type', 'Authorization']

export function ParameterEditor({
  endpointId,
  parameters,
  editable,
  onChanged,
}: {
  endpointId: string
  parameters: ParameterResponse[]
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const create = useCreateParameter()
  const update = useUpdateParameter()
  const [order, setOrder] = useState<string[]>([])

  // Local order overrides the server order during/after a drag until the next refetch.
  const sorted = applyOrder(parameters, order)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const addParameter = async () => {
    try {
      await create.mutateAsync({ endpointId, data: { name: 'param', location: 'query' } })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  const onDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) {
      return
    }
    const ids = sorted.map((p) => p.id!)
    const next = arrayMove(ids, ids.indexOf(active.id as string), ids.indexOf(over.id as string))
    setOrder(next) // optimistic
    try {
      // Persist the new positions; only rows whose index changed need an update.
      await Promise.all(
        next.map((id, index) =>
          sorted.find((p) => p.id === id)?.sortOrder === index
            ? null
            : update.mutateAsync({ parameterId: id, data: { sortOrder: index } }),
        ),
      )
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  return (
    <div className="space-y-3">
      {sorted.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t('endpoints.params.empty')}</p>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext items={sorted.map((p) => p.id!)} strategy={verticalListSortingStrategy}>
            <div className="space-y-2">
              {sorted.map((param) => (
                <ParameterRow
                  key={param.id}
                  param={param}
                  editable={editable}
                  onChanged={onChanged}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      )}

      {editable && (
        <Button variant="outline" size="sm" onClick={() => void addParameter()} disabled={create.isPending}>
          <Plus />
          {t('endpoints.params.add')}
        </Button>
      )}
    </div>
  )
}

function ParameterRow({
  param,
  editable,
  onChanged,
}: {
  param: ParameterResponse
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const update = useUpdateParameter()
  const remove = useDeleteParameter()
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: param.id!,
    disabled: !editable,
  })

  const commit = async (data: Record<string, unknown>) => {
    try {
      await update.mutateAsync({ parameterId: param.id!, data })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  const del = async () => {
    try {
      await remove.mutateAsync({ parameterId: param.id! })
      await onChanged()
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.deleteFailed')))
    }
  }

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        'rounded-lg border bg-card p-3',
        isDragging && 'opacity-60 shadow-lg',
      )}
    >
      <div className="flex items-start gap-2">
        {editable && (
          <button
            type="button"
            className="mt-2 cursor-grab text-muted-foreground active:cursor-grabbing"
            aria-label="Reorder"
            {...attributes}
            {...listeners}
          >
            <GripVertical className="size-4" />
          </button>
        )}
        <div className="grid flex-1 grid-cols-2 gap-2 sm:grid-cols-12">
          <div className="sm:col-span-4">
            <Input
              defaultValue={param.name}
              disabled={!editable}
              aria-label={t('endpoints.params.name')}
              onBlur={(e) => e.target.value !== param.name && void commit({ name: e.target.value })}
            />
          </div>
          <div className="sm:col-span-3">
            <Select
              value={param.location}
              disabled={!editable}
              onValueChange={(v) => void commit({ location: v })}
            >
              <SelectTrigger aria-label={t('endpoints.params.in')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {LOCATIONS.map((loc) => (
                  <SelectItem key={loc} value={loc}>
                    {t(`endpoints.params.location${loc.charAt(0).toUpperCase() + loc.slice(1)}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="sm:col-span-3">
            <Input
              defaultValue={param.dataType}
              disabled={!editable}
              aria-label={t('endpoints.params.type')}
              onBlur={(e) => e.target.value !== param.dataType && void commit({ dataType: e.target.value })}
            />
          </div>
          <div className="flex items-center gap-2 sm:col-span-2">
            <Switch
              checked={param.required}
              disabled={!editable}
              onCheckedChange={(checked) => void commit({ required: checked })}
              aria-label={t('endpoints.params.required')}
            />
            <span className="text-xs text-muted-foreground">{t('endpoints.params.required')}</span>
          </div>
          <div className="sm:col-span-6">
            <Input
              defaultValue={param.description ?? ''}
              disabled={!editable}
              placeholder={t('endpoints.params.description')}
              onBlur={(e) =>
                e.target.value !== (param.description ?? '') && void commit({ description: e.target.value })
              }
            />
          </div>
          <div className="sm:col-span-6">
            <Input
              defaultValue={param.example ?? ''}
              disabled={!editable}
              placeholder={t('endpoints.params.example')}
              onBlur={(e) =>
                e.target.value !== (param.example ?? '') && void commit({ example: e.target.value })
              }
            />
          </div>
          {editable && param.location === 'header' && (
            <div className="flex flex-wrap gap-1 sm:col-span-12">
              <span className="text-xs text-muted-foreground">{t('endpoints.params.headerSuggest')}:</span>
              {COMMON_HEADERS.map((header) => (
                <button
                  key={header}
                  type="button"
                  className="rounded-md border px-1.5 py-0.5 text-xs hover:bg-accent"
                  onClick={() => void commit({ name: header })}
                >
                  {header}
                </button>
              ))}
            </div>
          )}
        </div>
        {editable && (
          <Button size="icon" variant="ghost" onClick={() => void del()} aria-label={t('common2.delete')}>
            <Trash2 className="size-4 text-destructive" />
          </Button>
        )}
      </div>
    </div>
  )
}

/** Applies an optimistic id order on top of the server list; unknown ids fall back to server order. */
function applyOrder(parameters: ParameterResponse[], order: string[]): ParameterResponse[] {
  if (order.length === 0) {
    return parameters
  }
  const byId = new Map(parameters.map((p) => [p.id!, p]))
  const ordered = order.map((id) => byId.get(id)).filter((p): p is ParameterResponse => !!p)
  const extras = parameters.filter((p) => !order.includes(p.id!))
  return [...ordered, ...extras]
}
