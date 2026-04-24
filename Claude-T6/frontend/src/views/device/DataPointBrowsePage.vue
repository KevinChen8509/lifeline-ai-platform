<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="router.back()" text><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <h3>数据点浏览 — 设备 #{{ deviceId }}</h3>
    </div>

    <div v-loading="loading">
      <EmptyState
        v-if="dataPoints.length === 0 && !loading"
        description="该设备暂无数据点"
      />
      <el-table v-else :data="dataPoints" stripe>
        <el-table-column prop="identifier" label="标识符" min-width="140" />
        <el-table-column prop="dataType" label="数据类型" width="120" />
        <el-table-column label="最新值" width="160">
          <template #default="{ row }">
            <span v-if="row.lastValue !== null" class="value-cell">{{ row.lastValue }}</span>
            <span v-else class="no-value">-</span>
          </template>
        </el-table-column>
        <el-table-column label="数据质量" width="100">
          <template #default="{ row }">
            <el-tag :type="row.quality >= 80 ? 'success' : row.quality >= 50 ? 'warning' : 'danger'" size="small">
              {{ row.quality }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后上报" width="180">
          <template #default="{ row }">{{ formatTime(row.lastReportAt) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import deviceService from '@/services/deviceService'
import EmptyState from '@/components/EmptyState.vue'
import type { DeviceDataPoint } from '@/types/device'

const route = useRoute()
const router = useRouter()
const deviceId = Number(route.params.id)
const dataPoints = ref<DeviceDataPoint[]>([])
const loading = ref(false)

function formatTime(t: string | null) {
  if (!t) return '-'
  return new Date(t).toLocaleString()
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await deviceService.getDataPoints(deviceId)
    dataPoints.value = res.data || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-container { padding: 20px; }

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.page-header h3 { margin: 0; font-size: 16px; }

.value-cell {
  font-family: monospace;
  font-weight: 500;
  color: #303133;
}

.no-value { color: #c0c4cc; }
</style>
