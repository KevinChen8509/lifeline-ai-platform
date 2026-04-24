<template>
  <el-dialog v-model="visible" :title="title" width="420px" @close="onCancel">
    <p>{{ message }}</p>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="danger" :loading="loading" @click="onConfirm">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const visible = ref(false)
const loading = ref(false)
const title = ref('确认操作')
const message = ref('')
const confirmText = ref('确认')

let resolveFn: ((val: boolean) => void) | null = null

function open(opts: { title?: string; message: string; confirmText?: string }): Promise<boolean> {
  title.value = opts.title || '确认操作'
  message.value = opts.message
  confirmText.value = opts.confirmText || '确认'
  visible.value = true
  return new Promise(resolve => { resolveFn = resolve })
}

function onConfirm() {
  visible.value = false
  resolveFn?.(true)
  resolveFn = null
}

function onCancel() {
  visible.value = false
  resolveFn?.(false)
  resolveFn = null
}

defineExpose({ open, setLoading: (v: boolean) => { loading.value = v } })
</script>
