import { ref, onUnmounted } from 'vue'

export function usePolling(fn: () => Promise<void>, intervalMs: number = 5000, immediate = true) {
  const timer = ref<ReturnType<typeof setInterval> | null>(null)
  const running = ref(false)

  async function poll() {
    if (running.value) return
    running.value = true
    try { await fn() } finally { running.value = false }
  }

  function start() {
    stop()
    if (immediate) poll()
    timer.value = setInterval(poll, intervalMs)
  }

  function stop() {
    if (timer.value) { clearInterval(timer.value); timer.value = null }
  }

  function restart() { stop(); start() }

  onUnmounted(stop)

  return { running, start, stop, restart }
}
