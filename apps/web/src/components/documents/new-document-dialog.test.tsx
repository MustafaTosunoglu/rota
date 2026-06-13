import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { NewDocumentDialog } from '@/components/documents/new-document-dialog'
import { Button } from '@/components/ui/button'

// The dialog navigates on success; stub the router so no RouterProvider is needed.
const navigate = vi.fn()
vi.mock('@tanstack/react-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@tanstack/react-router')>()),
  useNavigate: () => navigate,
}))

function renderDialog() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <NewDocumentDialog trigger={<Button>New document</Button>} />
    </QueryClientProvider>,
  )
}

describe('NewDocumentDialog', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    navigate.mockReset()
  })

  it('validates required name and does not call the API', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'New document' }))
    await userEvent.click(screen.getByRole('button', { name: 'Create document' }))

    expect(await screen.findByText('This field is required.')).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('creates a document and navigates to it on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 'doc-1', name: 'Payments API', slug: 'payments-api' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'New document' }))
    await userEvent.type(screen.getByLabelText('Name'), 'Payments API')
    await userEvent.click(screen.getByRole('button', { name: 'Create document' }))

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: '/app/documents/$docId',
        params: { docId: 'doc-1' },
      }),
    )
    const [, init] = vi.mocked(globalThis.fetch).mock.calls[0]
    expect(init?.method).toBe('POST')
  })
})
