import { createFileRoute, redirect } from '@tanstack/react-router'

import { isAuthenticated } from '@/stores/auth'

// No landing page in Faz 1 — root forwards to the right place.
export const Route = createFileRoute('/')({
  beforeLoad: () => {
    throw redirect({ to: isAuthenticated() ? '/app/dashboard' : '/login' })
  },
})
