import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'src/routeTree.gen.ts']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // Route files export Route objects, shadcn/ui files export cva variants alongside
    // components, and lib context modules pair a Provider with its hook — all by-design
    // patterns where the Fast-Refresh "components only" rule does not apply.
    files: ['src/routes/**/*.tsx', 'src/components/ui/**/*.tsx', 'src/lib/**/*.tsx'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
])
