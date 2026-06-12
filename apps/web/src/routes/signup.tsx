import { useState } from 'react'
import { Link, createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { AuthCard } from '@/components/auth/auth-card'
import { SignupForm } from '@/components/auth/signup-form'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

export const Route = createFileRoute('/signup')({
  component: SignupPage,
})

function SignupPage() {
  const { t } = useTranslation()
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null)

  return (
    <AuthCard
      title={t('auth.signup.title')}
      description={t('auth.signup.description')}
      footer={
        <>
          {t('auth.signup.haveAccount')}{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            {t('auth.signup.loginLink')}
          </Link>
        </>
      }
    >
      {registeredEmail ? (
        <div className="space-y-4">
          <Alert variant="success">
            <AlertDescription>{t('auth.signup.success', { email: registeredEmail })}</AlertDescription>
          </Alert>
          <Button asChild className="w-full">
            <Link to="/login">{t('auth.signup.loginLink')}</Link>
          </Button>
        </div>
      ) : (
        <SignupForm onSuccess={setRegisteredEmail} />
      )}
    </AuthCard>
  )
}
