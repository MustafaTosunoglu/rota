import { useQueryClient } from '@tanstack/react-query'
import { Link, createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Trash2 } from 'lucide-react'
import {
  getGetEndpointQueryKey,
  getListEndpointsQueryKey,
  useDeleteEndpoint,
  useGetEndpoint,
  useListCategories,
} from '@rota/api-client'

import { GeneralTab } from '@/components/editor/general-tab'
import { ParameterEditor } from '@/components/editor/parameter-editor'
import { RequestBodyEditor } from '@/components/editor/request-body-editor'
import { ResponseEditor } from '@/components/editor/response-editor'
import { TryItPanel } from '@/components/try-it/try-it-panel'
import { MethodBadge } from '@/components/endpoints/method-badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { apiErrorMessage } from '@/lib/errors'
import { useWorkspace } from '@/lib/workspace'
import { toast } from 'sonner'
import { useNavigate } from '@tanstack/react-router'

export const Route = createFileRoute('/app/documents/$docId/endpoints/$endpointId')({
  component: EndpointEditorPage,
})

function EndpointEditorPage() {
  const { t } = useTranslation()
  const { docId, endpointId } = Route.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { editingVersion, editable } = useWorkspace()
  const versionId = editingVersion.id!

  const endpoint = useGetEndpoint(endpointId)
  const categories = useListCategories(versionId)
  const remove = useDeleteEndpoint()

  const refetchEndpoint = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: getGetEndpointQueryKey(endpointId) }),
      queryClient.invalidateQueries({ queryKey: getListEndpointsQueryKey(versionId) }),
    ])

  if (endpoint.isLoading) {
    return (
      <div className="mx-auto w-full max-w-3xl p-6">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="mt-4 h-64 w-full" />
      </div>
    )
  }

  if (endpoint.isError || !endpoint.data) {
    return <div className="p-6 text-sm text-muted-foreground">{t('common.errors.unexpected')}</div>
  }

  const data = endpoint.data

  const deleteEndpoint = async () => {
    try {
      await remove.mutateAsync({ endpointId })
      await queryClient.invalidateQueries({ queryKey: getListEndpointsQueryKey(versionId) })
      toast.success(t('endpoints.deleted'))
      void navigate({ to: '/app/documents/$docId/endpoints', params: { docId } })
    } catch (error) {
      toast.error(apiErrorMessage(error, t('common2.deleteFailed')))
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-5 p-6">
      <div>
        <Link
          to="/app/documents/$docId/endpoints"
          params={{ docId }}
          className="mb-3 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          {t('endpoints.title')}
        </Link>
        <div className="flex items-center gap-3">
          <MethodBadge method={data.method ?? 'GET'} />
          <span className="flex-1 truncate font-mono text-lg">{data.path}</span>
          {editable && (
            <ConfirmDialog
              title={t('endpoints.title')}
              description={t('documents.deleteConfirmBody', { name: `${data.method} ${data.path}` })}
              onConfirm={deleteEndpoint}
              trigger={
                <Button variant="ghost" size="icon" aria-label={t('common2.delete')}>
                  <Trash2 className="size-4 text-destructive" />
                </Button>
              }
            />
          )}
        </div>
      </div>

      {!editable && (
        <Alert>
          <AlertDescription>
            {t('endpoints.readOnlyBanner', { status: editingVersion.status })}
          </AlertDescription>
        </Alert>
      )}

      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general">{t('endpoints.tabs.general')}</TabsTrigger>
          <TabsTrigger value="parameters">{t('endpoints.tabs.parameters')}</TabsTrigger>
          <TabsTrigger value="body">{t('endpoints.tabs.body')}</TabsTrigger>
          <TabsTrigger value="responses">{t('endpoints.tabs.responses')}</TabsTrigger>
          <TabsTrigger value="tryit">{t('tryit.title')}</TabsTrigger>
          <TabsTrigger value="code">{t('endpoints.tabs.codeSamples')}</TabsTrigger>
        </TabsList>

        <TabsContent value="general">
          <GeneralTab
            endpoint={data}
            categories={categories.data ?? []}
            editable={editable}
            onChanged={refetchEndpoint}
          />
        </TabsContent>
        <TabsContent value="parameters">
          <ParameterEditor
            endpointId={endpointId}
            parameters={data.parameters ?? []}
            editable={editable}
            onChanged={refetchEndpoint}
          />
        </TabsContent>
        <TabsContent value="body">
          <RequestBodyEditor
            endpointId={endpointId}
            requestBodies={data.requestBodies ?? []}
            editable={editable}
            onChanged={refetchEndpoint}
          />
        </TabsContent>
        <TabsContent value="responses">
          <ResponseEditor
            endpointId={endpointId}
            responses={data.responses ?? []}
            editable={editable}
            onChanged={refetchEndpoint}
          />
        </TabsContent>
        <TabsContent value="tryit">
          <TryItPanel
            endpointId={endpointId}
            method={data.method ?? 'GET'}
            path={data.path ?? '/'}
            versionId={versionId}
          />
        </TabsContent>
        <TabsContent value="code">
          <p className="py-10 text-center text-sm text-muted-foreground">
            {t('endpoints.codeSamples.placeholder')}
          </p>
        </TabsContent>
      </Tabs>
    </div>
  )
}
