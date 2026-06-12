import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, createFileRoute } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { ApiError, useResetPassword } from '@rota/api-client'

import { AuthCard } from '@/components/auth/auth-card'
import { FieldError } from '@/components/auth/field-error'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export const Route = createFileRoute('/reset-password')({
  validateSearch: (search: Record<string, unknown>) => ({
    token: typeof search.token === 'string' ? search.token : '',
  }),
  component: ResetPasswordPage,
})

const schema = z.object({
  newPassword: z.string().min(10, 'validation.passwordMin').max(200, 'validation.passwordMax'),
})

type FormValues = z.infer<typeof schema>

function ResetPasswordPage() {
  const { t } = useTranslation()
  const { token } = Route.useSearch()
  const reset = useResetPassword()
  const [done, setDone] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = async (values: FormValues) => {
    setServerError(null)
    try {
      await reset.mutateAsync({ data: { token, newPassword: values.newPassword } })
      setDone(true)
    } catch (error) {
      if (error instanceof ApiError && (error.status === 400 || error.status === 401 || error.status === 404)) {
        setServerError(t('auth.reset.invalidToken'))
      } else {
        setServerError(t('common.errors.unexpected'))
      }
    }
  }

  return (
    <AuthCard
      title={t('auth.reset.title')}
      description={t('auth.reset.description')}
      footer={
        <Link to="/login" className="font-medium text-primary hover:underline">
          {t('auth.forgot.backToLogin')}
        </Link>
      }
    >
      {!token ? (
        <Alert variant="destructive">
          <AlertDescription>{t('auth.reset.missingToken')}</AlertDescription>
        </Alert>
      ) : done ? (
        <div className="space-y-4">
          <Alert variant="success">
            <AlertDescription>{t('auth.reset.success')}</AlertDescription>
          </Alert>
          <Button asChild className="w-full">
            <Link to="/login">{t('auth.login.submit')}</Link>
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {serverError && (
            <Alert variant="destructive">
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}
          <div className="space-y-2">
            <Label htmlFor="newPassword">{t('auth.reset.newPassword')}</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={!!errors.newPassword}
              {...register('newPassword')}
            />
            <FieldError message={errors.newPassword && t(errors.newPassword.message ?? '')} />
          </div>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {t('auth.reset.submit')}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}
