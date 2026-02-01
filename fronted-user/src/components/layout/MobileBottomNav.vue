<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Grid2X2, Rss, UserRound } from 'lucide-vue-next'

const route = useRoute()

const items = computed(() => [
    { label: '订阅', to: '/subscriptions', icon: Rss },
    { label: '发现', to: '/discover', icon: Grid2X2 },
    // { label: '收藏', to: '/favorites', icon: Bookmark },
    { label: '我的', to: '/profile', icon: UserRound }
])

const isActive = (path: string) => route.path.startsWith(path)
</script>

<template>
    <nav class="fixed inset-x-0 bottom-0 z-50 border-t border-border bg-card pb-safe">
        <div class="flex h-16 items-center justify-around">
            <RouterLink v-for="item in items" :key="item.to" :to="item.to"
                class="flex flex-1 flex-col items-center justify-center gap-1 py-2 transition-colors" :class="isActive(item.to)
                        ? 'text-primary'
                        : 'text-muted-foreground'
                    ">
                <component :is="item.icon" class="h-5 w-5" />
                <span class="text-[10px] font-medium">{{ item.label }}</span>
            </RouterLink>
        </div>
    </nav>
</template>
