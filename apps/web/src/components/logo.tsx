import { cn } from '@/lib/utils'

/** Placeholder letter-mark logo (CLAUDE.md): "R" in the primary color. */
export function Logo({ className, withWordmark = true }: { className?: string; withWordmark?: boolean }) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <svg
        viewBox="0 0 32 32"
        className="size-8"
        role="img"
        aria-label="Rota"
      >
        <rect width="32" height="32" rx="8" className="fill-primary" />
        <text
          x="16"
          y="22.5"
          textAnchor="middle"
          fontFamily="Inter, sans-serif"
          fontSize="18"
          fontWeight="700"
          className="fill-primary-foreground"
        >
          R
        </text>
      </svg>
      {withWordmark && <span className="text-lg font-semibold tracking-tight">Rota</span>}
    </div>
  )
}
