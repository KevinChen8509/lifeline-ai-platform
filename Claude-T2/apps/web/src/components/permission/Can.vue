<template>
  <slot v-if="hasPermission" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { usePermissionStore } from '@/stores/permission';

interface Props {
  /** 操作类型 */
  action: string;
  /** 资源类型 */
  subject: string;
}

const props = defineProps<Props>();

const permissionStore = usePermissionStore();

const hasPermission = computed(() => {
  return permissionStore.can(props.action, props.subject);
});
</script>
