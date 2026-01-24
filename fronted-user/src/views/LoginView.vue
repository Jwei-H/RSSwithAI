<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '../services/frontApi'
import { useSessionStore } from '../stores/session'
import { useToastStore } from '../stores/toast'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const toast = useToastStore()

const username = ref((route.query.username as string) || '')
const password = ref('')
const loading = ref(false)
const error = ref('')

const submit = async () => {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const { token } = await authApi.login({ username: username.value, password: password.value })
    session.setToken(token)
    const profile = await authApi.profile()
    session.setProfile(profile)
    toast.push('登录成功', 'success')
    const redirect = (route.query.redirect as string) || '/subscriptions'
    router.replace(redirect)
  } catch (err) {
    error.value = '用户名或密码错误'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-muted/40 px-4">
    <div class="w-full max-w-md rounded-3xl border border-border bg-card p-8 shadow-sm">
      <h1 class="text-lg font-semibold text-foreground">欢迎回来</h1>
      <p class="mt-1 text-xs text-muted-foreground">登录后进入 RSSwithAI 前台</p>

      <div class="mt-6 space-y-4">
        <div>
          <label class="text-xs text-muted-foreground">用户名</label>
          <input v-model="username" class="mt-2 w-full rounded-xl border border-border px-3 py-2 text-sm" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground">密码</label>
          <input
            v-model="password"
            type="password"
            class="mt-2 w-full rounded-xl border border-border px-3 py-2 text-sm"
          />
        </div>
        <p v-if="error" class="text-xs text-rose-600">{{ error }}</p>
        <button
          class="w-full rounded-xl bg-primary px-3 py-2 text-sm text-primary-foreground"
          :disabled="loading"
          @click="submit"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>

      <div class="mt-6 text-center text-xs text-muted-foreground">
        还没有账号？
        <RouterLink to="/register" class="text-primary hover:underline">去注册</RouterLink>
      </div>
    </div>
  </div>
</template>
