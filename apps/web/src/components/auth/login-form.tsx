import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { ApiError, useLogin } from '@rota/api-client'

import { FieldError } from '@/components/auth/field-error'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuthStore } from '@/stores/auth'

const schema = z.object({
  email: z.string().email('validation.emailInvalid').max(254),
  password: z.string().min(1, 'validation.required'),
})

type FormValues = z.infer<typeof schema>

export function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const { t } = useTranslation()
  const login = useLogin()
  const setSession = useAuthStore((s) => s.setSession)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = async (values: FormValues) => {
    setServerError(null)
    try {
      const tokens = await login.mutateAsync({ data: values })
      setSession(tokens)
      onSuccess()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setServerError(t('auth.login.invalidCredentials'))
      } else if (error instanceof ApiError && error.problem?.detail) {
        setServerError(error.problem.detail)
      } else {
        setServerError(t('common.errors.unexpected'))
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      {serverError && (
        <Alert variant="destructive">
          <AlertDescription>{serverError}</AlertDescription>
        </Alert>
      )}
      <div className="space-y-2">
        <Label htmlFor="email">{t('auth.email')}</Label>
        <Input
          id="email"
          type="email"
          autoComplete="email"
          aria-invalid={!!errors.email}
          {...register('email')}
        />
        <FieldError message={errors.email && t(errors.email.message ?? '')} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="password">{t('auth.password')}</Label>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          aria-invalid={!!errors.password}
          {...register('password')}
        />
        <FieldError message={errors.password && t(errors.password.message ?? '')} />
      </div>
      <Button type="submit" className="w-full" disabled={isSubmitting}>
        {t('auth.login.submit')}
      </Button>
    </form>
  )
}
