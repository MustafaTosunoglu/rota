import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import en from '@/locales/en.json'
import tr from '@/locales/tr.json'

const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('rota-locale') : null
const browser = typeof navigator !== 'undefined' && navigator.language.startsWith('tr') ? 'tr' : 'en'

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    tr: { translation: tr },
  },
  lng: stored ?? browser,
  fallbackLng: 'en',
  interpolation: {
    // React already escapes interpolated values.
    escapeValue: false,
  },
})

export function setLocale(locale: 'en' | 'tr') {
  localStorage.setItem('rota-locale', locale)
  void i18n.changeLanguage(locale)
}

export default i18n
