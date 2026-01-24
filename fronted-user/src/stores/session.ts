import { computed, reactive } from 'vue'

export type UserProfile = {
  id: number
  username: string
  avatarUrl?: string | null
}

type SessionState = {
  token: string
  profile: UserProfile | null
}

const state = reactive<SessionState>({
  token: '',
  profile: null
})

export function initSession() {
  const token = localStorage.getItem('rss_token')
  const profileRaw = localStorage.getItem('rss_profile')
  if (token) state.token = token
  if (profileRaw) {
    try {
      state.profile = JSON.parse(profileRaw)
    } catch {
      state.profile = null
    }
  }
}

export function useSessionStore() {
  const isAuthed = computed(() => Boolean(state.token))

  const setToken = (token: string) => {
    state.token = token
    localStorage.setItem('rss_token', token)
  }

  const setProfile = (profile: UserProfile | null) => {
    state.profile = profile
    if (profile) {
      localStorage.setItem('rss_profile', JSON.stringify(profile))
    } else {
      localStorage.removeItem('rss_profile')
    }
  }

  const clear = () => {
    state.token = ''
    state.profile = null
    localStorage.removeItem('rss_token')
    localStorage.removeItem('rss_profile')
  }

  return { state, isAuthed, setToken, setProfile, clear }
}

export function getToken() {
  return state.token
}

export function isAuthenticated() {
  return Boolean(state.token)
}
