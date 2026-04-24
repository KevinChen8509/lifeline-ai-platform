<template>
  <div class="page-container">
    <!-- 顶部操作 -->
    <div class="top-bar">
      <div class="filters">
        <el-select v-model="subStore.filters.status" placeholder="状态" clearable style="width:120px" @change="onFilter">
          <el-option label="启用" :value="0" />
          <el-option label="暂停" :value="1" />
        </el-select>
      </div>
      <el-button type="primary" @click="router.push({ name: 'SubscriptionCreate' })">
        <el-icon><Plus /></el-icon> 创建订阅
      </el-button>
    </div>

    <!-- 批量操作栏 -->
    <BatchActionBar
      :count="subStore.selectedIds.length"
      @clear="subStore.clearSelection()"
      @activate="batchToggle(0)"
      @pause="batchToggle(1)"
    />

    <!-- 订阅列表 -->
    <PaginatedTable
      :data="subStore.subscriptions"
      :loading="subStore.loading"
      :page="subStore.pagination.page"
      :total-pages="subStore.pagination.totalPages"
      :total-elements="subStore.pagination.totalElements"
      @page-change="onPageChange"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="50" :selectable="(row: any) => row.status !== 2" />
      <el-table-column prop="name" label="订阅名称" min-width="160" />
      <el-table-column label="端点" min-width="140">
        <template #default="{ row }">{{ row.endpointName || row.endpointId }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.subscriptionType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规则数" width="80">
        <template #default="{ row }">{{ row.rules?.length || 0 }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="warning" @click="toggleStatus(row, 1)">暂停</el-button>
          <el-button v-if="row.status === 1" link type="success" @click="toggleStatus(row, 0)">启用</el-button>
          <el-button link type="primary" @click="router.push({ name: 'SubscriptionCreate', query: { edit: row.id } })">编辑</el-button>
          <el-button link type="danger" @click="deleteSub(row)">删除</el-button>
        </template>
      </el-table-column>
    </PaginatedTable>

    <ConfirmDialog ref="confirmRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useSubscriptionStore } from '@/stores/subscription'
import PaginatedTable from '@/components/PaginatedTable.vue'
import BatchActionBar from '@/components/BatchActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const router = useRouter()
const subStore = useSubscriptionStore()
const confirmRef = ref()

function typeLabel(t: number) {
  return ['设备级', '设备类型级', '分组级'][t] || '未知'
}

function statusType(s: number) {
  return ['success', 'warning', 'info'][s] as any
}

function formatTime(t: string) {
  if (!t) return '-'
  return new Date(t).toLocaleString()
}

function onFilter() {
  subStore.fetchSubscriptions({ page: 0 })
}

function onPageChange(page: number) {
  subStore.fetchSubscriptions({ page })
}

function onSelectionChange(rows: any[]) {
  subStore.selectedIds = rows.map((r: any) => r.id)
}

async function toggleStatus(row: any, status: number) {
  await subStore.toggleStatus(row.id, status)
  ElMessage.success(status === 0 ? '已启用' : '已暂停')
}

async function batchToggle(status: number) {
  await subStore.batchToggleStatus(status)
  ElMessage.success(status === 0 ? '批量启用成功' : '批量暂停成功')
}

async function deleteSub(row: any) {
  const ok = await confirmRef.value?.open({
    title: '删除订阅',
    message: `确定要删除订阅「${row.name}」吗？此操作不可恢复。`,
    confirmText: '确认删除'
  })
  if (!ok) return
  await subStore.deleteSubscription(row.id)
  ElMessage.success('订阅已删除')
}

onMounted(() => {
  subStore.fetchSubscriptions({ page: 0 })
})
</script>

<style scoped>
.page-container { padding: 20px; }

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 12px;
}
</style>
