import { createContext, useContext } from 'react'
import type { DocumentResponse, VersionResponse } from '@rota/api-client'

export interface WorkspaceValue {
  documentId: string
  document: DocumentResponse
  versions: VersionResponse[]
  /** The draft version, if one exists (the editable target). */
  draftVersion: VersionResponse | null
  /** The version the editor reads/writes: the draft, else the most recent (read-only). */
  editingVersion: VersionResponse
  /** True when {@link editingVersion} is a draft and therefore mutable. */
  editable: boolean
  refetch: () => void
}

const WorkspaceContext = createContext<WorkspaceValue | null>(null)

export function WorkspaceProvider({ value, children }: { value: WorkspaceValue; children: React.ReactNode }) {
  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}

/** Document workspace state, available to every /app/documents/$docId/* child route. */
export function useWorkspace(): WorkspaceValue {
  const value = useContext(WorkspaceContext)
  if (!value) {
    throw new Error('useWorkspace must be used within a document workspace route')
  }
  return value
}
