export type Theme = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'rota-theme'

export function getTheme(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'light' || stored === 'dark' ? stored : 'system'
}

/** Applies the theme to <html> and persists it; index.html replays this before first paint. */
export function setTheme(theme: Theme) {
  if (theme === 'system') {
    localStorage.removeItem(STORAGE_KEY)
  } else {
    localStorage.setItem(STORAGE_KEY, theme)
  }
  const dark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
}

export function isDarkApplied(): boolean {
  return document.documentElement.classList.contains('dark')
}
