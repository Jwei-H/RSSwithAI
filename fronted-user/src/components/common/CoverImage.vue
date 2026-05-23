<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { unescapeUrl } from '../../utils/text'
import { rewriteUrl } from '../../utils/url-rewrites'

const props = withDefaults(defineProps<{
  src?: string | null
  alt?: string
  containerClass?: string
  imgClass?: string
}>(), {
  alt: '',
  containerClass: '',
  imgClass: 'h-full w-full object-cover'
})

const failed = ref(false)

const normalizedSrc = computed(() => {
  if (!props.src) return ''
  return rewriteUrl(unescapeUrl(props.src))
})

const visible = computed(() => Boolean(normalizedSrc.value) && !failed.value)

watch(normalizedSrc, () => {
  failed.value = false
})

const onError = () => {
  failed.value = true
}
</script>

<template>
  <div v-if="visible" :class="containerClass">
    <img
      :src="normalizedSrc"
      :alt="alt"
      :class="imgClass"
      loading="lazy"
      referrerpolicy="no-referrer"
      @error="onError"
    />
  </div>
</template>
