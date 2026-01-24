import { onMounted, onUnmounted, ref, watch } from 'vue'

export function useInfiniteScroll(
  onLoadMore: () => void,
  root?: { value: HTMLElement | null }
) {
  const sentinel = ref<HTMLElement | null>(null)
  let observer: IntersectionObserver | null = null

  const observe = () => {
    if (!sentinel.value) return
    observer?.disconnect()
    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          onLoadMore()
        }
      },
      { threshold: 0.1, root: root?.value ?? null }
    )
    observer.observe(sentinel.value)
  }

  onMounted(observe)

  watch(() => root?.value, observe)
  watch(() => sentinel.value, observe)

  onUnmounted(() => {
    observer?.disconnect()
  })

  return { sentinel }
}
