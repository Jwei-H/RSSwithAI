<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, subscriptionApi } from '../services/frontApi'
import { useToastStore } from '../stores/toast'
import { useSessionStore } from '../stores/session'

const router = useRouter()
const toast = useToastStore()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const confirm = ref('')
const loading = ref(false)
const error = ref('')

const submit = async () => {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次密码输入不一致'
    return
  }
  loading.value = true
  try {
    await authApi.register({ username: username.value, password: password.value })
    try {
      const loginRes = await authApi.login({ username: username.value, password: password.value })
      session.setToken(loginRes.token)
      await Promise.all([
        subscriptionApi.create({ type: 'RSS', targetId: 8 }),
        subscriptionApi.create({ type: 'TOPIC', targetId: 17 })
      ])
    } catch (subscribeError: any) {
      toast.push(subscribeError?.message || '默认订阅添加失败', 'error')
    } finally {
      session.clear()
    }
    toast.push('注册成功，请登录', 'success')
    router.push({ path: '/login', query: { username: username.value } })
  } catch (err: any) {
    error.value = err?.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-muted/40 px-4">
    <div class="w-full max-w-md rounded-3xl border border-border bg-card p-8 shadow-sm">
      <h1 class="text-lg font-semibold text-foreground">创建账号</h1>
      <p class="mt-1 text-xs text-muted-foreground">注册后可订阅、收藏和查看 AI 摘要</p>

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
        <div>
          <label class="text-xs text-muted-foreground">确认密码</label>
          <input
            v-model="confirm"
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
          {{ loading ? '提交中...' : '注册' }}
        </button>
      </div>

      <div class="mt-6 text-center text-xs text-muted-foreground">
        已有账号？
        <RouterLink to="/login" class="text-primary hover:underline">去登录</RouterLink>
      </div>
    </div>
  </div>
</template>
