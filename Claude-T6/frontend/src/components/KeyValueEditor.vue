<template>
  <div class="kv-editor">
    <div v-for="(item, idx) in pairs" :key="idx" class="kv-row">
      <el-input v-model="item.key" placeholder="Header 名称" class="kv-key" @change="emitUpdate" />
      <span class="kv-sep">:</span>
      <el-input v-model="item.value" placeholder="值" class="kv-value" @change="emitUpdate" />
      <el-button link type="danger" @click="removePair(idx)"><el-icon><Delete /></el-icon></el-button>
    </div>
    <el-button link type="primary" @click="addPair">+ 添加</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'

const props = defineProps<{ modelValue?: Record<string, string> }>()
const emit = defineEmits<{ 'update:modelValue': [val: Record<string, string>] }>()

const pairs = ref<{ key: string; value: string }[]>([])

watch(() => props.modelValue, (val) => {
  if (!val) { pairs.value = []; return }
  pairs.value = Object.entries(val).map(([key, value]) => ({ key, value }))
}, { immediate: true })

function addPair() { pairs.value.push({ key: '', value: '' }) }
function removePair(idx: number) { pairs.value.splice(idx, 1); emitUpdate() }

function emitUpdate() {
  const result: Record<string, string> = {}
  for (const p of pairs.value) {
    if (p.key.trim()) result[p.key.trim()] = p.value
  }
  emit('update:modelValue', result)
}
</script>

<style scoped>
.kv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.kv-key { width: 180px; }
.kv-value { flex: 1; }
.kv-sep { color: #999; }
</style>
