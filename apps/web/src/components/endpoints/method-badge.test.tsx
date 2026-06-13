import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { MethodBadge } from '@/components/endpoints/method-badge'

describe('MethodBadge', () => {
  it('uppercases the method', () => {
    render(<MethodBadge method="get" />)
    expect(screen.getByText('GET')).toBeInTheDocument()
  })

  it('renders known methods with distinct color classes', () => {
    const { rerender } = render(<MethodBadge method="GET" />)
    const get = screen.getByText('GET').className
    rerender(<MethodBadge method="DELETE" />)
    const del = screen.getByText('DELETE').className
    expect(get).not.toEqual(del)
  })
})
