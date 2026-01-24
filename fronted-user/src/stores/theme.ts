import { reactive } from 'vue'

export type ThemeMode = 'system' | 'light' | 'dark'

type ThemeState = {
  mode: ThemeMode
  systemPrefersDark: boolean
}

const THEME_KEY = 'rsswithai-theme'
let mediaQuery: MediaQueryList | null = null
let initialized = false

const state = reactive<ThemeState>({
  mode: 'system',
  systemPrefersDark: false
})

const getEffectiveMode = () => {
  if (state.mode === 'system') {
    return state.systemPrefersDark ? 'dark' : 'light'
  }
  return state.mode
}

const applyTheme = () => {
  const effective = getEffectiveMode()
  document.documentElement.classList.toggle('dark', effective === 'dark')
  document.documentElement.style.colorScheme = effective
}

const setMode = (mode: ThemeMode) => {
  state.mode = mode
  localStorage.setItem(THEME_KEY, mode)
  applyTheme()
}

const initTheme = () => {
  if (initialized) return
  initialized = true

  const saved = localStorage.getItem(THEME_KEY) as ThemeMode | null
  if (saved === 'light' || saved === 'dark' || saved === 'system') {
    state.mode = saved
  }

  if (typeof window !== 'undefined' && 'matchMedia' in window) {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    state.systemPrefersDark = mediaQuery.matches
    mediaQuery.addEventListener('change', (event) => {
      state.systemPrefersDark = event.matches
      if (state.mode === 'system') {
        applyTheme()
      }
    })
  }

  applyTheme()
}

export function useThemeStore() {
  return {
    state,
    get effectiveMode() {
      return getEffectiveMode()
    },
    setMode,
    initTheme
  }
}