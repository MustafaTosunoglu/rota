import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/app/documents/$docId/')({
  beforeLoad: ({ params }) => {
    throw redirect({ to: '/app/documents/$docId/overview', params: { docId: params.docId } })
  },
})
