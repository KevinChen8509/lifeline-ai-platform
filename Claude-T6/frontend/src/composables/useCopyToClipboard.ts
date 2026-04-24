import { ref } from 'vue'

export function useCopyToClipboard() {
  const copied = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      copied.value = true
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => { copied.value = false }, 2000)
      return true
    } catch {
      copied.value = false
      return false
    }
  }

  return { copied, copy }
}
