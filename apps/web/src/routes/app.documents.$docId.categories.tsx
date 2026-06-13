import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Pencil, Plus, Trash2, X } from 'lucide-react'
import {
  getListCategoriesQueryKey,
  useCreateCategory,
  useDeleteCategory,
  useListCategories,
  useUpdateCategory,
  type CategoryResponse,
} from '@rota/api-client'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { apiErrorMessage } from '@/lib/errors'
import { useWorkspace } from '@/lib/workspace'

export const Route = createFileRoute('/app/documents/$docId/categories')({
  component: CategoriesPage,
})

function CategoriesPage() {
  const { t } = useTranslation()
  const { editingVersion, editable } = useWorkspace()
  const versionId = editingVersion.id!
  const queryClient = useQueryClient()
  const categories = useListCategories(versionId)
  const create = useCreateCategory()

  const [newName, setNewName] = useState('')
  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: getListCategoriesQueryKey(versionId) })

  const addCategory = async () => {
    if (!newName.trim()) {
      return
    }
    try {
      await create.mutateAsync({ versionId, data: { name: newName.trim() } })
      await invalidate()
      setNewName('')
      toast.success(t('categories.created'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6 p-6">
      <h1 className="text-xl font-semibold tracking-tight">{t('categories.title')}</h1>

      {editable && (
        <div className="flex gap-2">
          <Input
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder={t('categories.name')}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                void addCategory()
              }
            }}
          />
          <Button onClick={() => void addCategory()} disabled={create.isPending || !newName.trim()}>
            <Plus />
            {t('common2.add')}
          </Button>
        </div>
      )}

      {categories.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : (categories.data?.length ?? 0) === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">{t('categories.empty')}</p>
      ) : (
        <div className="space-y-2">
          {categories.data!.map((category) => (
            <CategoryRow
              key={category.id}
              category={category}
              editable={editable}
              onChanged={invalidate}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function CategoryRow({
  category,
  editable,
  onChanged,
}: {
  category: CategoryResponse
  editable: boolean
  onChanged: () => Promise<unknown>
}) {
  const { t } = useTranslation()
  const update = useUpdateCategory()
  const remove = useDeleteCategory()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(category.name ?? '')

  const save = async () => {
    try {
      await update.mutateAsync({ categoryId: category.id!, data: { name: name.trim() } })
      await onChanged()
      setEditing(false)
      toast.success(t('categories.updated'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.saveFailed')))
    }
  }

  const del = async () => {
    try {
      await remove.mutateAsync({ categoryId: category.id! })
      await onChanged()
      toast.success(t('categories.deleted'))
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.deleteFailed')))
    }
  }

  return (
    <Card>
      <CardContent className="flex items-center gap-3 py-3">
        {editing ? (
          <>
            <Input value={name} onChange={(e) => setName(e.target.value)} className="flex-1" autoFocus />
            <Button size="sm" onClick={() => void save()} disabled={update.isPending}>
              {t('common2.save')}
            </Button>
            <Button size="icon" variant="ghost" onClick={() => setEditing(false)}>
              <X />
            </Button>
          </>
        ) : (
          <>
            <span className="flex-1 truncate font-medium">{category.name}</span>
            {editable && (
              <>
                <Button size="icon" variant="ghost" onClick={() => setEditing(true)}>
                  <Pencil />
                </Button>
                <ConfirmDialog
                  title={t('categories.title')}
                  description={t('categories.deleteConfirm')}
                  onConfirm={del}
                  trigger={
                    <Button size="icon" variant="ghost">
                      <Trash2 className="text-destructive" />
                    </Button>
                  }
                />
              </>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
