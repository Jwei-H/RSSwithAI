import { reactive } from 'vue'

export type ThemeMode = 'system' | 'light' | 'dark'

type ThemeState = {
  mode: ThemeMode
  systemPrefersDark: boolean
  fontSizeLevel: number // 1 to 4, default 2
}

const THEME_KEY = 'rsswithai-theme'
const FONT_SIZE_KEY = 'rsswithai-font-size'
let mediaQuery: MediaQueryList | null = null
let initialized = false

const state = reactive<ThemeState>({
  mode: 'system',
  systemPrefersDark: false,
  fontSizeLevel: 2
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
  
  // Apply font classes up to level 4
  document.documentElement.classList.remove('mobile-font-level-1', 'mobile-font-level-2', 'mobile-font-level-3', 'mobile-font-level-4')
  document.documentElement.classList.add(`mobile-font-level-${state.fontSizeLevel}`)
}

const setMode = (mode: ThemeMode) => {
  state.mode = mode
  localStorage.setItem(THEME_KEY, mode)
  applyTheme()
}

const setFontSizeLevel = (level: number) => {
  if (level >= 1 && level <= 4) {
    state.fontSizeLevel = level
    localStorage.setItem(FONT_SIZE_KEY, String(level))
    applyTheme()
  }
}

const initTheme = () => {
  if (initialized) return
  initialized = true

  const saved = localStorage.getItem(THEME_KEY) as ThemeMode | null
  if (saved === 'light' || saved === 'dark' || saved === 'system') {
    state.mode = saved
  }
  
  const savedFont = localStorage.getItem(FONT_SIZE_KEY)
  if (savedFont) {
    const level = parseInt(savedFont, 10)
    if (!isNaN(level) && level >= 1 && level <= 4) {
      state.fontSizeLevel = level
    }
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
    setFontSizeLevel,
    initTheme
  }
}