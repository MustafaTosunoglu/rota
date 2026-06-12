import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { ApiError, useRegister } from '@rota/api-client'

import { FieldError } from '@/components/auth/field-error'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// Mirrors the backend's RegisterRequest bean validation.
const schema = z.object({
  organizationName: z.string().min(1, 'validation.required').max(120),
  displayName: z.string().min(1, 'validation.required').max(120),
  email: z.string().email('validation.emailInvalid').max(254),
  password: z.string().min(10, 'validation.passwordMin').max(200, 'validation.passwordMax'),
})

type FormValues = z.infer<typeof schema>

export function SignupForm({ onSuccess }: { onSuccess: (email: string) => void }) {
  const { t, i18n } = useTranslation()
  const signup = useRegister()
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = async (values: FormValues) => {
    setServerError(null)
    try {
      await signup.mutateAsync({ data: { ...values, locale: i18n.language } })
      onSuccess(values.email)
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setServerError(t('auth.signup.emailTaken'))
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
        <Label htmlFor="organizationName">{t('auth.signup.organizationName')}</Label>
        <Input id="organizationName" aria-invalid={!!errors.organizationName} {...register('organizationName')} />
        <FieldError message={errors.organizationName && t(errors.organizationName.message ?? '')} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="displayName">{t('auth.signup.displayName')}</Label>
        <Input
          id="displayName"
          autoComplete="name"
          aria-invalid={!!errors.displayName}
          {...register('displayName')}
        />
        <FieldError message={errors.displayName && t(errors.displayName.message ?? '')} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="email">{t('auth.email')}</Label>
        <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register('email')} />
        <FieldError message={errors.email && t(errors.email.message ?? '')} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="password">{t('auth.password')}</Label>
        <Input
          id="password"
          type="password"
          autoComplete="new-password"
          aria-invalid={!!errors.password}
          {...register('password')}
        />
        <FieldError message={errors.password && t(errors.password.message ?? '')} />
      </div>
      <Button type="submit" className="w-full" disabled={isSubmitting}>
        {t('auth.signup.submit')}
      </Button>
    </form>
  )
}
