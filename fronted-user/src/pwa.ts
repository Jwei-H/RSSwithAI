import { registerSW } from 'virtual:pwa-register'

export function registerPwa() {
  if (!import.meta.env.PROD) {
    return
  }

  registerSW({
    immediate: true,
  })
}
