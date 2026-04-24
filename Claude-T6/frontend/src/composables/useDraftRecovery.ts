import { ref, watch, onMounted } from 'vue'

export function useDraftRecovery<T extends Record<string, any>>(key: string, initial: T) {
  const draft = ref<T>({ ...initial }) as { value: T }

  onMounted(() => {
    const saved = localStorage.getItem(`draft:${key}`)
    if (saved) {
      try { draft.value = { ...initial, ...JSON.parse(saved) } } catch { /* ignore */ }
    }
  })

  watch(draft, (val) => {
    localStorage.setItem(`draft:${key}`, JSON.stringify(val))
  }, { deep: true })

  function clearDraft() {
    localStorage.removeItem(`draft:${key}`)
    draft.value = { ...initial }
  }

  return { draft, clearDraft }
}
