<template>
  <div class="page-container">
    <div class="device-layout">
      <!-- 左侧设备树 -->
      <div class="tree-panel">
        <h4>设备分组</h4>
        <el-tree
          :data="deviceStore.deviceTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          highlight-current
          default-expand-all
          @node-click="onNodeClick"
        >
          <template #default="{ node, data }">
            <span class="tree-node">
              <span>{{ node.label }}</span>
              <el-tag size="small" type="info" class="tree-count">{{ data.deviceCount }}</el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右侧设备列表 -->
      <div class="list-panel">
        <div class="list-header">
          <span>设备列表</span>
        </div>
        <PaginatedTable
          :data="deviceStore.deviceList"
          :loading="deviceStore.loading"
          :page="deviceStore.pagination.page"
          :total-pages="deviceStore.pagination.totalPages"
          :total-elements="deviceStore.pagination.totalElements"
          @page-change="onPageChange"
        >
          <el-table-column prop="deviceName" label="设备名称" min-width="140" />
          <el-table-column prop="deviceKey" label="设备标识" min-width="160" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后活跃" width="180">
            <template #default="{ row }">{{ formatTime(row.lastActiveAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewDataPoints(row)">数据点</el-button>
            </template>
          </el-table-column>
        </PaginatedTable>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import PaginatedTable from '@/components/PaginatedTable.vue'
import type { TreeNode } from '@/types/device'

const router = useRouter()
const deviceStore = useDeviceStore()

function statusType(s: number) {
  return [undefined, 'success', 'info', 'danger'][s] as any
}

function statusLabel(s: number) {
  return ['未激活', '在线', '离线', '禁用'][s] || '未知'
}

function formatTime(t: string | null) {
  if (!t) return '-'
  return new Date(t).toLocaleString()
}

function onNodeClick(node: TreeNode) {
  deviceStore.fetchDevices({ productId: node.id, page: 0 })
}

function onPageChange(page: number) {
  deviceStore.fetchDevices({ page })
}

function viewDataPoints(row: any) {
  router.push({ name: 'DataPointBrowse', params: { id: row.id } })
}

onMounted(() => {
  deviceStore.fetchGroupTree()
  deviceStore.fetchDevices({ page: 0 })
})
</script>

<style scoped>
.page-container { padding: 20px; }

.device-layout {
  display: flex;
  gap: 20px;
  height: calc(100vh - 160px);
}

.tree-panel {
  width: 260px;
  background: #fff;
  border-radius: 4px;
  padding: 16px;
  overflow-y: auto;
  flex-shrink: 0;
}

.tree-panel h4 { margin: 0 0 12px; }

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.tree-count { margin-left: auto; }

.list-panel {
  flex: 1;
  background: #fff;
  border-radius: 4px;
  padding: 16px;
  overflow: auto;
}

.list-header {
  margin-bottom: 12px;
  font-weight: 500;
  font-size: 15px;
}
</style>
