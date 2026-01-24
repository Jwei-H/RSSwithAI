import { reactive } from 'vue'

export type ToastType = 'info' | 'success' | 'error'

export type Toast = {
  id: string
  type: ToastType
  message: string
}

const state = reactive<{ toasts: Toast[] }>({
  toasts: []
})

export function useToastStore() {
  const push = (message: string, type: ToastType = 'info', timeout = 2400) => {
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`
    state.toasts.push({ id, type, message })
    if (timeout > 0) {
      setTimeout(() => remove(id), timeout)
    }
  }

  const remove = (id: string) => {
    const index = state.toasts.findIndex((toast) => toast.id === id)
    if (index >= 0) state.toasts.splice(index, 1)
  }

  return { state, push, remove }
}
