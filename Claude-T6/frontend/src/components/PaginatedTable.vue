<template>
  <div class="paginated-table">
    <el-table v-bind="$attrs" :data="data" :loading="loading" stripe>
      <slot />
    </el-table>
    <div class="pagination-wrap" v-if="totalPages > 1">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="totalElements"
        layout="total, prev, pager, next"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  data: any[]
  loading?: boolean
  page: number
  totalPages: number
  totalElements: number
  pageSize?: number
}>()

const emit = defineEmits<{ 'page-change': [page: number] }>()

const currentPage = computed({
  get: () => props.page + 1,
  set: () => {}
})

function onPageChange(p: number) {
  emit('page-change', p - 1)
}
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
