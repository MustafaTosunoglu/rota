import { useState } from 'react'
import { Moon, Sun } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { isDarkApplied, setTheme } from '@/lib/theme'

export function ThemeToggle() {
  const [dark, setDark] = useState(isDarkApplied)

  const toggle = () => {
    const next = !dark
    setDark(next)
    setTheme(next ? 'dark' : 'light')
  }

  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label="Toggle theme">
      {dark ? <Sun /> : <Moon />}
    </Button>
  )
}
