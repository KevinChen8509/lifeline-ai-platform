<template>
  <transition name="slide-up">
    <div v-if="count > 0" class="batch-bar">
      <span>已选择 <strong>{{ count }}</strong> 项</span>
      <el-button @click="$emit('clear')">取消选择</el-button>
      <el-button type="success" @click="$emit('activate')">批量启用</el-button>
      <el-button type="warning" @click="$emit('pause')">批量暂停</el-button>
      <slot />
    </div>
  </transition>
</template>

<script setup lang="ts">
defineProps<{ count: number }>()
defineEmits<{ clear: []; activate: []; pause: [] }>()
</script>

<style scoped>
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  margin-bottom: 12px;
}

.slide-up-enter-active, .slide-up-leave-active { transition: all 0.3s; }
.slide-up-enter-from, .slide-up-leave-to { opacity: 0; transform: translateY(10px); }
</style>
