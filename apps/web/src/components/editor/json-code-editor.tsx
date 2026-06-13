import { useState } from 'react'
import CodeMirror from '@uiw/react-codemirror'
import { json } from '@codemirror/lang-json'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import { isDarkApplied } from '@/lib/theme'

/**
 * JSON editor (plan 3.10, CodeMirror) with inline validity feedback. Holds local text so the
 * user can type invalid intermediate states; {@code onValidChange} only fires for parseable
 * JSON (or empty). {@code value} seeds the editor at mount — to reset it for a different
 * entity, give the component a {@code key}. Lives in route-split chunks, so CodeMirror never
 * loads on light pages.
 */
export function JsonCodeEditor({
  value,
  onValidChange,
  editable = true,
  minHeight = '8rem',
}: {
  value: string
  onValidChange: (text: string) => void
  editable?: boolean
  minHeight?: string
}) {
  const { t } = useTranslation()
  const [text, setText] = useState(value)
  const [error, setError] = useState(false)

  const handleChange = (next: string) => {
    setText(next)
    if (next.trim() === '') {
      setError(false)
      onValidChange('')
      return
    }
    try {
      JSON.parse(next)
      setError(false)
      onValidChange(next)
    } catch {
      setError(true)
    }
  }

  return (
    <div className="space-y-1">
      <div className={cn('overflow-hidden rounded-lg border', error && 'border-destructive')}>
        <CodeMirror
          value={text}
          editable={editable}
          theme={isDarkApplied() ? 'dark' : 'light'}
          extensions={[json()]}
          minHeight={minHeight}
          basicSetup={{ lineNumbers: true, foldGutter: false, highlightActiveLine: editable }}
          onChange={handleChange}
        />
      </div>
      {error && <p className="text-xs text-destructive">{t('endpoints.body.invalidJson')}</p>}
    </div>
  )
}
