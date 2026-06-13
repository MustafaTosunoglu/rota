import { Toaster as Sonner, type ToasterProps } from 'sonner'

import { isDarkApplied } from '@/lib/theme'

/** App-wide toast host. Theme is read once at mount (matches the no-flash boot script). */
export function Toaster(props: ToasterProps) {
  return <Sonner theme={isDarkApplied() ? 'dark' : 'light'} richColors closeButton {...props} />
}
