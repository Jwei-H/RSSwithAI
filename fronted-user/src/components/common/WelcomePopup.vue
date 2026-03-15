<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import { useSessionStore } from '../../stores/session'

const session = useSessionStore()
const md = new MarkdownIt()

const content = `
## 欢迎来到 RSSwithAI 👋

这里是您的高效阅读中心，包含以下功能：
- **智能摘要**
- **结构化阅读**
- **事件驱动订阅**
- **词云与热点趋势**
- **AI赋能的搜索与推荐**

---
获取更多项目信息 : [GitHub](https://github.com/Jwei-H/RSSwithAI)
`

const htmlContent = computed(() => md.render(content))

const close = () => {
    session.closeWelcome()
}
</script>

<template>
    <div v-if="session.state.welcomeShown"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
        <!-- 弹窗容器 -->
        <div
            class="w-full max-w-lg overflow-hidden rounded-2xl bg-card border border-border shadow-lg animate-in fade-in zoom-in duration-300">

            <!-- 顶部图片/标题区域 (可选) -->
            <div class="h-2 bg-gradient-to-r from-primary to-accent"></div>

            <!-- 内容区域 -->
            <div class="p-6 max-h-[70vh] overflow-y-auto custom-scrollbar">
                <div class="markdown-body" v-html="htmlContent"></div>
            </div>

            <!-- 底部按钮 -->
            <div class="flex justify-end border-t border-border bg-muted/30 p-4">
                <button @click="close"
                    class="rounded-xl bg-primary px-6 py-2.5 text-sm font-medium text-primary-foreground shadow-sm hover:bg-primary/90 transition-all active:scale-95">
                    我知道了
                </button>
            </div>
        </div>
    </div>
</template>

<style scoped>
/* 简单的滚动条样式 */
.custom-scrollbar::-webkit-scrollbar {
    width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
    background-color: var(--muted-foreground);
    opacity: 0.2;
    border-radius: 3px;
}

.custom-scrollbar::-webkit-scrollbar-track {
    background: transparent;
}
</style>