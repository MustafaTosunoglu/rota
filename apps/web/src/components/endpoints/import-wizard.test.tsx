import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { ImportWizard } from '@/components/endpoints/import-wizard'
import { Button } from '@/components/ui/button'

function renderWizard() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ImportWizard versionId="v-1" trigger={<Button>Open import</Button>} />
    </QueryClientProvider>,
  )
}

/** Routes the two import calls (parse, apply) by URL to the right canned response. */
function mockImportFetch() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = typeof input === 'string' ? input : (input as Request).url
    if (url.includes('/import/parse')) {
      return new Response(
        JSON.stringify({
          suggestedTitle: 'Petstore',
          environments: [],
          categories: [{ name: 'pets' }],
          endpoints: [{ method: 'GET', path: '/pets', categoryName: 'pets' }],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      )
    }
    if (url.includes('/import')) {
      return new Response(JSON.stringify({ created: 1, overwritten: 0, skipped: 0 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
}

describe('ImportWizard', () => {
  afterEach(() => vi.restoreAllMocks())

  it('parses content, shows a preview, then applies the import', async () => {
    const fetchSpy = mockImportFetch()
    renderWizard()

    await userEvent.click(screen.getByRole('button', { name: 'Open import' }))
    // paste (not type) — the JSON braces are special key descriptors for userEvent.type.
    await userEvent.click(screen.getByLabelText('or paste the content'))
    await userEvent.paste('{"openapi":"3.0.0"}')
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }))

    // Preview shows the detected endpoint.
    expect(await screen.findByText('/pets')).toBeInTheDocument()
    expect(screen.getByText(/Detected 1 endpoint/)).toBeInTheDocument()

    // In the preview step the only "Import" button is the confirm action.
    await userEvent.click(screen.getByRole('button', { name: 'Import' }))

    await waitFor(() => {
      const applyCall = fetchSpy.mock.calls.find(([u]) => String(u).endsWith('/versions/v-1/import'))
      expect(applyCall).toBeDefined()
    })
  })

  it('does not parse with empty content (Preview disabled)', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    renderWizard()

    await userEvent.click(screen.getByRole('button', { name: 'Open import' }))
    expect(screen.getByRole('button', { name: 'Preview' })).toBeDisabled()
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
