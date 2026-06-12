import { useEffect, useRef, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, createFileRoute } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { useResendVerification, useVerifyEmail } from '@rota/api-client'

import { AuthCard } from '@/components/auth/auth-card'
import { FieldError } from '@/components/auth/field-error'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export const Route = createFileRoute('/verify-email')({
  validateSearch: (search: Record<string, unknown>) => ({
    token: typeof search.token === 'string' ? search.token : '',
  }),
  component: VerifyEmailPage,
})

type Status = 'verifying' | 'success' | 'failed'

function VerifyEmailPage() {
  const { t } = useTranslation()
  const { token } = Route.useSearch()
  const verify = useVerifyEmail()
  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'failed')
  const fired = useRef(false)

  useEffect(() => {
    // Single-use token: guard against StrictMode's double effect invocation.
    if (!token || fired.current) {
      return
    }
    fired.current = true
    verify
      .mutateAsync({ data: { token } })
      .then(() => setStatus('success'))
      .catch(() => setStatus('failed'))
  }, [token, verify])

  return (
    <AuthCard
      title={t('auth.verify.title')}
      footer={
        <Link to="/login" className="font-medium text-primary hover:underline">
          {t('auth.verify.goToLogin')}
        </Link>
      }
    >
      {status === 'verifying' && (
        <p className="text-sm text-muted-foreground">{t('auth.verify.verifying')}</p>
      )}
      {status === 'success' && (
        <div className="space-y-4">
          <Alert variant="success">
            <AlertDescription>{t('auth.verify.success')}</AlertDescription>
          </Alert>
          <Button asChild className="w-full">
            <Link to="/login">{t('auth.verify.goToLogin')}</Link>
          </Button>
        </div>
      )}
      {status === 'failed' && <ResendSection />}
    </AuthCard>
  )
}

const resendSchema = z.object({
  email: z.string().email('validation.emailInvalid').max(254),
})

type ResendValues = z.infer<typeof resendSchema>

function ResendSection() {
  const { t } = useTranslation()
  const resend = useResendVerification()
  const [sent, setSent] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResendValues>({ resolver: zodResolver(resendSchema) })

  const onSubmit = async (values: ResendValues) => {
    // Like forgot-password: always report success, no account enumeration.
    await resend.mutateAsync({ data: values }).catch(() => undefined)
    setSent(true)
  }

  if (sent) {
    return (
      <Alert variant="success">
        <AlertDescription>{t('auth.verify.resent')}</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-4">
      <Alert variant="destructive">
        <AlertDescription>{t('auth.verify.invalidToken')}</AlertDescription>
      </Alert>
      <p className="text-sm text-muted-foreground">{t('auth.verify.resendPrompt')}</p>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="email">{t('auth.email')}</Label>
          <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register('email')} />
          <FieldError message={errors.email && t(errors.email.message ?? '')} />
        </div>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {t('auth.verify.resend')}
        </Button>
      </form>
    </div>
  )
}
