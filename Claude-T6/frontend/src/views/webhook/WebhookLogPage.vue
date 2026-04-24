<template>
  <div class="page-container">
    <!-- 顶部筛选 + 统计 -->
    <div class="top-bar">
      <div class="filters">
        <el-select v-model="logStore.filters.status" placeholder="状态" clearable style="width:120px" @change="onFilter">
          <el-option label="待发送" value="PENDING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="重试中" value="RETRYING" />
          <el-option label="已死亡" value="DEAD" />
        </el-select>
        <el-select v-model="logStore.filters.endpointId" placeholder="端点" clearable style="width:160px" @change="onFilter">
          <el-option v-for="ep in endpointStore.endpoints" :key="ep.id" :label="ep.name" :value="ep.id" />
        </el-select>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div v-if="logStore.stats" class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ logStore.stats.totalLast24h }}</div>
        <div class="stat-label">24h 推送总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value" :class="logStore.stats.successRate >= 95 ? 'text-success' : 'text-danger'">
          {{ (logStore.stats.successRate * 100).toFixed(1) }}%
        </div>
        <div class="stat-label">成功率</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ logStore.stats.avgLatencyMs }}ms</div>
        <div class="stat-label">平均延迟</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ logStore.stats.p95LatencyMs }}ms</div>
        <div class="stat-label">P95 延迟</div>
      </el-card>
    </div>

    <!-- 日志列表 -->
    <PaginatedTable
      :data="logStore.logs"
      :loading="logStore.loading"
      :page="logStore.pagination.page"
      :total-pages="logStore.pagination.totalPages"
      :total-elements="logStore.pagination.totalElements"
      @page-change="onPageChange"
    >
      <el-table-column prop="eventId" label="事件 ID" width="160" show-overflow-tooltip />
      <el-table-column prop="event" label="事件类型" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="StormGuard" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.stormGuardDegraded" type="warning" size="small" filterable>降级</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="attemptCount" label="尝试次数" width="90" />
      <el-table-column prop="responseCode" label="HTTP 状态" width="100" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'FAILED' || row.status === 'DEAD'" link type="warning" @click="retryLog(row)">重试</el-button>
        </template>
      </el-table-column>
    </PaginatedTable>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="推送详情" width="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="事件 ID">{{ detail.eventId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="HTTP 状态码">{{ detail.responseCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="尝试次数">{{ detail.attemptCount }} / {{ detail.maxRetries }}</el-descriptions-item>
          <el-descriptions-item label="下次重试">{{ formatTime(detail.nextRetryAt) }}</el-descriptions-item>
          <el-descriptions-item label="推送时间">{{ formatTime(detail.deliveredAt) }}</el-descriptions-item>
          <el-descriptions-item label="StormGuard 降级">{{ detail.stormGuardDegraded ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="规则跳过">{{ detail.ruleMatchSkipped ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.errorMsg" class="error-section">
          <h4>错误信息</h4>
          <pre class="error-msg">{{ detail.errorMsg }}</pre>
        </div>
        <div v-if="detail.responseBody" class="response-section">
          <h4>响应内容</h4>
          <JsonViewer :data="safeParseJson(detail.responseBody)" />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useWebhookLogStore } from '@/stores/webhookLog'
import { useEndpointStore } from '@/stores/endpoint'
import PaginatedTable from '@/components/PaginatedTable.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import type { DeliveryLog } from '@/types/webhook-log'

const logStore = useWebhookLogStore()
const endpointStore = useEndpointStore()

const detailVisible = ref(false)
const detail = ref<DeliveryLog | null>(null)

function statusType(s: string) {
  const map: Record<string, any> = { SUCCESS: 'success', FAILED: 'danger', PENDING: 'info', RETRYING: 'warning', DEAD: '' }
  return map[s] || 'info'
}

function formatTime(t: string | null) {
  if (!t) return '-'
  return new Date(t).toLocaleString()
}

function safeParseJson(str: string | null) {
  if (!str) return null
  try { return JSON.parse(str) } catch { return str }
}

function onFilter() {
  logStore.fetchLogs({ page: 0 })
}

function onPageChange(page: number) {
  logStore.fetchLogs({ page })
}

async function viewDetail(row: DeliveryLog) {
  const data = await logStore.fetchDetail(row.id)
  detail.value = data || null
  detailVisible.value = true
}

async function retryLog(row: DeliveryLog) {
  await logStore.retryLog(row.id)
  ElMessage.success('重试已触发')
}

onMounted(() => {
  endpointStore.fetchEndpoints()
  logStore.fetchLogs({ page: 0 })
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

.filters { display: flex; gap: 12px; }

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }

.error-section, .response-section { margin-top: 16px; }
.error-section h4, .response-section h4 { margin: 0 0 8px; font-size: 14px; }
.error-msg { background: #fef0f0; padding: 12px; border-radius: 4px; font-size: 13px; margin: 0; white-space: pre-wrap; }
</style>
