import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

import '@/lib/i18n'
import i18n from '@/lib/i18n'

// Deterministic language regardless of the host machine's locale.
void i18n.changeLanguage('en')

afterEach(() => {
  cleanup()
  localStorage.clear()
})
