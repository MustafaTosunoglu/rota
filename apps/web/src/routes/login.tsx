import { Link, createFileRoute, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { AuthCard } from '@/components/auth/auth-card'
import { LoginForm } from '@/components/auth/login-form'

export const Route = createFileRoute('/login')({
  component: LoginPage,
})

function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <AuthCard
      title={t('auth.login.title')}
      description={t('auth.login.description')}
      footer={
        <>
          {t('auth.login.noAccount')}{' '}
          <Link to="/signup" className="font-medium text-primary hover:underline">
            {t('auth.login.signupLink')}
          </Link>
        </>
      }
    >
      <div className="space-y-4">
        <LoginForm onSuccess={() => void navigate({ to: '/app/dashboard' })} />
        <div className="text-center">
          <Link to="/forgot-password" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            {t('auth.login.forgotLink')}
          </Link>
        </div>
      </div>
    </AuthCard>
  )
}
