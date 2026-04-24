<template>
  <el-alert v-if="conflicts.length > 0" type="warning" :closable="false" class="conflict-warning">
    <template #title>
      <el-icon><WarningFilled /></el-icon>
      检测到 {{ conflicts.length }} 条规则冲突
    </template>
    <ul class="conflict-list">
      <li v-for="(c, idx) in conflicts" :key="idx">{{ c }}</li>
    </ul>
  </el-alert>
</template>

<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'

defineProps<{ conflicts: string[] }>()
</script>

<style scoped>
.conflict-warning { margin-bottom: 16px; }
.conflict-list { margin: 4px 0 0; padding-left: 20px; font-size: 13px; }
</style>
