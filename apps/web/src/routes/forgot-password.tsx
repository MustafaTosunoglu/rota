import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, createFileRoute } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { useForgotPassword } from '@rota/api-client'

import { AuthCard } from '@/components/auth/auth-card'
import { FieldError } from '@/components/auth/field-error'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export const Route = createFileRoute('/forgot-password')({
  component: ForgotPasswordPage,
})

const schema = z.object({
  email: z.string().email('validation.emailInvalid').max(254),
})

type FormValues = z.infer<typeof schema>

function ForgotPasswordPage() {
  const { t } = useTranslation()
  const forgot = useForgotPassword()
  const [sent, setSent] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = async (values: FormValues) => {
    // Backend always answers 204 (no account enumeration) — so does the UI.
    await forgot.mutateAsync({ data: values }).catch(() => undefined)
    setSent(true)
  }

  return (
    <AuthCard
      title={t('auth.forgot.title')}
      description={t('auth.forgot.description')}
      footer={
        <Link to="/login" className="font-medium text-primary hover:underline">
          {t('auth.forgot.backToLogin')}
        </Link>
      }
    >
      {sent ? (
        <Alert variant="success">
          <AlertDescription>{t('auth.forgot.done')}</AlertDescription>
        </Alert>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="email">{t('auth.email')}</Label>
            <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register('email')} />
            <FieldError message={errors.email && t(errors.email.message ?? '')} />
          </div>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {t('auth.forgot.submit')}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}
