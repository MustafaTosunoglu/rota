import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Download } from 'lucide-react'
import { exportOpenApi } from '@rota/api-client'

import { Button } from '@/components/ui/button'
import { apiErrorMessage } from '@/lib/errors'

/** Fetches the version's OpenAPI export and downloads it as a JSON file. */
export function ExportButton({ versionId, fileBase }: { versionId: string; fileBase: string }) {
  const { t } = useTranslation()
  const [busy, setBusy] = useState(false)

  const download = async () => {
    setBusy(true)
    try {
      const spec = await exportOpenApi(versionId)
      const blob = new Blob([JSON.stringify(spec, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${slug(fileBase)}-openapi.json`
      link.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast.error(apiErrorMessage(error, t('export.failed')))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Button variant="outline" size="sm" onClick={() => void download()} disabled={busy}>
      <Download />
      {t('export.openapi')}
    </Button>
  )
}

function slug(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-+|-+$)/g, '') || 'api'
}
