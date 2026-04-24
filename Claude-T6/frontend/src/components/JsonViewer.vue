<template>
  <div class="json-viewer">
    <pre class="json-content"><code>{{ formatted }}</code></pre>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: any }>()

const formatted = computed(() => {
  try { return JSON.stringify(props.data, null, 2) }
  catch { return String(props.data) }
})
</script>

<style scoped>
.json-viewer {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  max-height: 400px;
  overflow: auto;
}

.json-content {
  margin: 0;
  font-size: 13px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
