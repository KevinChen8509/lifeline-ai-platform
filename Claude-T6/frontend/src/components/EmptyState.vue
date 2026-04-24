<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="imageSize">
      <template v-if="$slots.default">
        <slot />
      </template>
      <template v-else-if="actionText">
        <el-button type="primary" @click="$emit('action')">{{ actionText }}</el-button>
      </template>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  description?: string
  actionText?: string
  imageSize?: number
}>()

defineEmits<{ action: [] }>()
</script>

<style scoped>
.empty-state {
  padding: 60px 0;
  text-align: center;
}
</style>
