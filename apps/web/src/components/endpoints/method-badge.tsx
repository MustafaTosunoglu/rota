import { cn } from '@/lib/utils'

const METHOD_STYLES: Record<string, string> = {
  GET: 'bg-emerald-600/15 text-emerald-700 dark:text-emerald-400',
  POST: 'bg-blue-600/15 text-blue-700 dark:text-blue-400',
  PUT: 'bg-amber-600/15 text-amber-700 dark:text-amber-400',
  PATCH: 'bg-violet-600/15 text-violet-700 dark:text-violet-400',
  DELETE: 'bg-red-600/15 text-red-700 dark:text-red-400',
  HEAD: 'bg-zinc-600/15 text-zinc-700 dark:text-zinc-300',
  OPTIONS: 'bg-zinc-600/15 text-zinc-700 dark:text-zinc-300',
}

/** Colored HTTP-method chip used in endpoint lists and the editor header. */
export function MethodBadge({ method, className }: { method: string; className?: string }) {
  const upper = method.toUpperCase()
  return (
    <span
      className={cn(
        'inline-flex min-w-14 items-center justify-center rounded-md px-1.5 py-0.5 font-mono text-xs font-semibold',
        METHOD_STYLES[upper] ?? METHOD_STYLES.OPTIONS,
        className,
      )}
    >
      {upper}
    </span>
  )
}
