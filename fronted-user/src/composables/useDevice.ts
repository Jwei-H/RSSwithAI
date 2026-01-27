import { ref, onMounted, onUnmounted, readonly } from 'vue'

const MOBILE_BREAKPOINT = 768

/**
 * Composable for detecting device type based on viewport width.
 * Uses md breakpoint (768px) as the threshold.
 */
export function useDevice() {
    const isMobile = ref(false)

    const update = () => {
        isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
    }

    onMounted(() => {
        update()
        window.addEventListener('resize', update)
    })

    onUnmounted(() => {
        window.removeEventListener('resize', update)
    })

    return {
        isMobile: readonly(isMobile)
    }
}
