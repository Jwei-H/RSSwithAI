<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Bookmark, Grid2X2, History, LogIn, Rss, UserRound } from 'lucide-vue-next'
import { useSessionStore } from '../../stores/session'

const route = useRoute()
const session = useSessionStore()

const items = computed(() => {
  if (!session.isAuthed.value) {
    return [
      { label: '频道广场', to: '/discover', icon: Grid2X2 },
      { label: '历史', to: '/history', icon: History }
    ]
  }
  return [
    { label: '订阅', to: '/subscriptions', icon: Rss },
    { label: '频道广场', to: '/discover', icon: Grid2X2 },
    { label: '收藏', to: '/favorites', icon: Bookmark },
    { label: '历史', to: '/history', icon: History }
  ]
})

const profileItem = computed(() =>
  session.isAuthed.value
    ? { label: '个人中心', to: '/profile', icon: UserRound }
    : { label: '登录', to: '/login', icon: LogIn }
)
</script>

<template>
  <aside class="flex w-20 flex-col items-center gap-4 border-r border-border bg-card px-3 py-6">
    <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
      <span class="text-sm font-semibold">R</span>
    </div>
    <nav class="flex flex-1 flex-col gap-3">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="flex h-11 w-11 items-center justify-center rounded-2xl transition"
        :class="
          route.path.startsWith(item.to)
            ? 'bg-primary text-primary-foreground shadow'
            : 'text-muted-foreground hover:bg-muted'
        "
      >
        <component :is="item.icon" class="h-5 w-5" />
      </RouterLink>
    </nav>
    <RouterLink
      :to="profileItem.to"
      class="flex h-11 w-11 items-center justify-center rounded-2xl transition"
      :class="
        route.path.startsWith(profileItem.to)
          ? 'bg-primary text-primary-foreground shadow'
          : 'text-muted-foreground hover:bg-muted'
      "
    >
      <component :is="profileItem.icon" class="h-5 w-5" />
    </RouterLink>
  </aside>
</template>
