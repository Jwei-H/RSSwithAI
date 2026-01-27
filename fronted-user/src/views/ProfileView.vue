<script setup lang="ts">
import { ref } from 'vue'
import PageShell from '../components/layout/PageShell.vue'
import { authApi } from '../services/frontApi'
import { useSessionStore } from '../stores/session'
import { useToastStore } from '../stores/toast'
import { useRouter } from 'vue-router'
import { useThemeStore } from '../stores/theme'

const session = useSessionStore()
const toast = useToastStore()
const router = useRouter()
const theme = useThemeStore()

const username = ref(session.state.profile?.username ?? '')
const newPassword = ref('')
const oldPassword = ref('')
const loading = ref(false)

const updateUsername = async () => {
  if (!username.value.trim()) return
  loading.value = true
  try {
    const profile = await authApi.updateUsername({ newUsername: username.value.trim() })
    session.setProfile(profile)
    toast.push('用户名已更新', 'success')
  } catch (error: any) {
    toast.push(error?.message || '更新失败', 'error')
  } finally {
    loading.value = false
  }
}

const updatePassword = async () => {
  if (!oldPassword.value || !newPassword.value) return
  loading.value = true
  try {
    await authApi.updatePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    oldPassword.value = ''
    newPassword.value = ''
    toast.push('密码已更新', 'success')
  } catch (error: any) {
    toast.push(error?.message || '更新失败', 'error')
  } finally {
    loading.value = false
  }
}

const logout = () => {
  session.clear()
  router.replace('/login')
}
</script>

<template>
  <PageShell>
    <template #sidebar>
      <div class="rounded-2xl border border-border bg-card p-4">
        <h2 class="text-sm font-semibold text-foreground">个人中心</h2>
        <p class="mt-2 text-xs text-muted-foreground">管理账号信息与安全设置</p>
      </div>
    </template>

    <template #main>
      <div class="rounded-2xl border border-border bg-card p-4 md:p-6">
        <h3 class="text-sm font-semibold text-foreground">账号信息</h3>
        <div class="mt-4 space-y-4">
          <div>
            <label class="text-xs text-muted-foreground">用户名</label>
            <input v-model="username" class="mt-2 w-full rounded-xl border border-border px-3 py-2 text-sm" />
            <button class="mt-3 rounded-xl bg-primary px-3 py-2 text-xs text-primary-foreground" :disabled="loading"
              @click="updateUsername">
              更新用户名
            </button>
          </div>
        </div>
      </div>

      <div class="rounded-2xl border border-border bg-card p-4 md:p-6">
        <h3 class="text-sm font-semibold text-foreground">修改密码</h3>
        <div class="mt-4 space-y-4">
          <div>
            <label class="text-xs text-muted-foreground">原密码</label>
            <input v-model="oldPassword" type="password"
              class="mt-2 w-full rounded-xl border border-border px-3 py-2 text-sm" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground">新密码</label>
            <input v-model="newPassword" type="password"
              class="mt-2 w-full rounded-xl border border-border px-3 py-2 text-sm" />
          </div>
          <button class="rounded-xl bg-primary px-3 py-2 text-xs text-primary-foreground" :disabled="loading"
            @click="updatePassword">
            更新密码
          </button>
        </div>
      </div>

      <div class="rounded-2xl border border-border bg-card p-4 md:p-6">
        <h3 class="text-sm font-semibold text-foreground">偏好设置</h3>
        <p class="mt-2 text-xs text-muted-foreground">主题将影响全部页面与卡片</p>
        <div class="mt-4 flex flex-wrap gap-2">
          <button class="rounded-xl border border-border px-3 py-2 text-xs transition"
            :class="theme.state.mode === 'system' ? 'bg-primary text-primary-foreground' : 'bg-muted/50 text-foreground hover:bg-muted'"
            @click="theme.setMode('system')">
            跟随系统
          </button>
          <button class="rounded-xl border border-border px-3 py-2 text-xs transition"
            :class="theme.state.mode === 'light' ? 'bg-primary text-primary-foreground' : 'bg-muted/50 text-foreground hover:bg-muted'"
            @click="theme.setMode('light')">
            浅色
          </button>
          <button class="rounded-xl border border-border px-3 py-2 text-xs transition"
            :class="theme.state.mode === 'dark' ? 'bg-primary text-primary-foreground' : 'bg-muted/50 text-foreground hover:bg-muted'"
            @click="theme.setMode('dark')">
            深色
          </button>
        </div>
        <p class="mt-3 text-xs text-muted-foreground">
          当前模式：{{ theme.effectiveMode === 'dark' ? '深色' : '浅色' }}
          <span v-if="theme.state.mode === 'system'">（跟随系统）</span>
        </p>
      </div>

      <!-- 移动端：账户操作 -->
      <div class="rounded-2xl border border-border bg-card p-4 md:hidden">
        <h3 class="text-sm font-semibold text-foreground">账户</h3>
        <p class="mt-2 text-xs text-muted-foreground">当前用户：{{ session.state.profile?.username }}</p>
        <button class="mt-4 w-full rounded-xl border border-border px-3 py-2 text-xs" @click="logout">
          退出登录
        </button>
      </div>
    </template>

    <template #right>
      <div class="rounded-2xl border border-border bg-card p-4">
        <h3 class="text-sm font-semibold text-foreground">账户状态</h3>
        <p class="mt-3 text-xs text-muted-foreground">当前用户：{{ session.state.profile?.username }}</p>
        <button class="mt-4 w-full rounded-xl border border-border px-3 py-2 text-xs" @click="logout">
          退出登录
        </button>
      </div>
    </template>
  </PageShell>
</template>
