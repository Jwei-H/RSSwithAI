<script setup lang="ts">
import { useUiStore } from '../../stores/ui'
import ArticleDetailPane from './ArticleDetailPane.vue'

const ui = useUiStore()
</script>

<template>
  <div class="fixed inset-0 z-40 bg-background">
    <ArticleDetailPane
      :articleId="ui.detailArticleId"
      :onClose="ui.closeDetail"
      :onOpenArticle="ui.openDetail"
    />
  </div>
</template>
