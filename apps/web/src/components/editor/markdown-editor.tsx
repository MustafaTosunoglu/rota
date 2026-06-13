import { useEffect } from 'react'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { Markdown } from 'tiptap-markdown'
import { Bold, Code, Italic, List, ListOrdered } from 'lucide-react'

import type { Editor } from '@tiptap/react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** tiptap-markdown augments editor.storage but ships no types; narrow it locally. */
function getMarkdown(editor: Editor): string {
  const storage = editor.storage as unknown as Record<string, { getMarkdown: () => string }>
  return storage.markdown.getMarkdown()
}

/**
 * Markdown editor (plan 3.13) built on TipTap. Reads/writes real Markdown via the markdown
 * extension, so the value round-trips with the backend's {@code descriptionMd} field.
 * {@code onChange} fires on blur to avoid a mutation per keystroke.
 */
export function MarkdownEditor({
  value,
  onCommit,
  editable = true,
  placeholder,
}: {
  value: string
  onCommit: (markdown: string) => void
  editable?: boolean
  placeholder?: string
}) {
  const editor = useEditor({
    editable,
    extensions: [StarterKit, Markdown.configure({ html: false })],
    content: value,
    editorProps: {
      attributes: {
        class: cn(
          'prose prose-sm dark:prose-invert max-w-none min-h-32 px-3 py-2 focus:outline-none',
          !editable && 'opacity-70',
        ),
        'aria-label': placeholder ?? 'Markdown editor',
      },
    },
    onBlur: ({ editor }) => {
      onCommit(getMarkdown(editor))
    },
  })

  // Sync external value changes (e.g. switching endpoints) without clobbering local edits.
  useEffect(() => {
    if (!editor) {
      return
    }
    const current = getMarkdown(editor)
    if (value !== current) {
      editor.commands.setContent(value, { emitUpdate: false })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, editor])

  useEffect(() => {
    editor?.setEditable(editable)
  }, [editable, editor])

  if (!editor) {
    return null
  }

  return (
    <div className="rounded-lg border">
      {editable && (
        <div className="flex items-center gap-1 border-b p-1">
          <ToolbarButton active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()}>
            <Bold className="size-4" />
          </ToolbarButton>
          <ToolbarButton
            active={editor.isActive('italic')}
            onClick={() => editor.chain().focus().toggleItalic().run()}
          >
            <Italic className="size-4" />
          </ToolbarButton>
          <ToolbarButton
            active={editor.isActive('code')}
            onClick={() => editor.chain().focus().toggleCode().run()}
          >
            <Code className="size-4" />
          </ToolbarButton>
          <ToolbarButton
            active={editor.isActive('bulletList')}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
          >
            <List className="size-4" />
          </ToolbarButton>
          <ToolbarButton
            active={editor.isActive('orderedList')}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
          >
            <ListOrdered className="size-4" />
          </ToolbarButton>
        </div>
      )}
      <EditorContent editor={editor} />
    </div>
  )
}

function ToolbarButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <Button
      type="button"
      variant={active ? 'secondary' : 'ghost'}
      size="icon"
      className="size-7"
      onClick={onClick}
    >
      {children}
    </Button>
  )
}
