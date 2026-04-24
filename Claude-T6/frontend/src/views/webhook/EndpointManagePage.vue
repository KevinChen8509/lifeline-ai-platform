<template>
  <div class="page-container">
    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <div class="quota-info" v-if="endpointStore.quota">
        <span>端点配额：</span>
        <el-progress
          :percentage="endpointStore.quota.percentage"
          :color="quotaColor"
          :stroke-width="12"
          style="width: 160px; display: inline-flex;"
        />
        <span class="quota-text">{{ endpointStore.quota.used }} / {{ endpointStore.quota.limit }}</span>
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 创建端点
      </el-button>
    </div>

    <!-- 端点卡片列表 -->
    <div v-loading="endpointStore.loading">
      <EmptyState
        v-if="endpointStore.endpoints.length === 0 && !endpointStore.loading"
        description="暂无 Webhook 端点"
        action-text="创建第一个端点"
        @action="openCreateDialog"
      />
      <div v-else class="endpoint-cards">
        <el-card v-for="ep in endpointStore.endpoints" :key="ep.id" class="endpoint-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="ep-name">{{ ep.name }}</span>
              <el-tag :type="ep.status === 0 ? 'success' : 'info'" size="small">
                {{ ep.status === 0 ? '启用' : '禁用' }}
              </el-tag>
            </div>
          </template>
          <div class="card-body">
            <div class="ep-url" :title="ep.url">{{ ep.url }}</div>
            <div class="ep-meta">
              <span v-if="ep.consecutiveFailures > 0" class="failure-badge">
                连续失败 {{ ep.consecutiveFailures }} 次
              </span>
              <span v-if="ep.lastPushAt">最后推送：{{ formatTime(ep.lastPushAt) }}</span>
            </div>
          </div>
          <div class="card-actions">
            <el-button link type="primary" @click="openEditDialog(ep)">编辑</el-button>
            <el-button link type="success" @click="testEndpoint(ep)">测试</el-button>
            <el-button link type="danger" @click="deleteEndpoint(ep)">删除</el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑端点' : '创建端点'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="端点名称" />
        </el-form-item>
        <el-form-item label="URL" prop="url">
          <el-input v-model="form.url" placeholder="https://example.com/webhook" />
        </el-form-item>
        <el-form-item label="自定义 Headers">
          <KeyValueEditor v-model="form.customHeaders" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 密钥展示弹窗 -->
    <el-dialog v-model="secretVisible" title="端点密钥" width="480px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" class="secret-alert">
        此密钥仅展示一次，请立即复制并妥善保存。关闭后将无法再次查看。
      </el-alert>
      <div class="secret-display">
        <code>{{ endpointStore.secretOnceVisible }}</code>
        <el-button link type="primary" @click="copySecret">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <ConfirmDialog ref="confirmRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useEndpointStore } from '@/stores/endpoint'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import KeyValueEditor from '@/components/KeyValueEditor.vue'
import type { WebhookEndpoint } from '@/types/endpoint'
import type { FormInstance, FormRules } from 'element-plus'

const endpointStore = useEndpointStore()
const { copied, copy } = useCopyToClipboard()
const confirmRef = ref()

const dialogVisible = ref(false)
const secretVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = ref({
  name: '',
  url: '',
  customHeaders: {} as Record<string, string>
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  url: [
    { required: true, message: '请输入 URL', trigger: 'blur' },
    { type: 'url', message: '请输入有效的 HTTPS URL', trigger: 'blur' }
  ]
}

const quotaColor = computed(() => {
  const p = endpointStore.quota?.percentage || 0
  if (p >= 100) return '#f56c6c'
  if (p >= 80) return '#e6a23c'
  return '#67c23a'
})

function formatTime(t: string | null) {
  if (!t) return '-'
  return new Date(t).toLocaleString()
}

function openCreateDialog() {
  isEdit.value = false
  editId.value = null
  form.value = { name: '', url: '', customHeaders: {} }
  dialogVisible.value = true
}

function openEditDialog(ep: WebhookEndpoint) {
  isEdit.value = true
  editId.value = ep.id
  form.value = {
    name: ep.name,
    url: ep.url,
    customHeaders: ep.customHeaders ? { ...ep.customHeaders } : {}
  }
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await endpointStore.updateEndpoint(editId.value, form.value)
      ElMessage.success('端点已更新')
      dialogVisible.value = false
    } else {
      await endpointStore.createEndpoint(form.value)
      dialogVisible.value = false
      secretVisible.value = true
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function testEndpoint(ep: WebhookEndpoint) {
  try {
    await endpointStore.fetchEndpoints()
    const res = await (await import('@/services/endpointService')).default.test(ep.id)
    ElMessage.success('测试推送已发送')
  } catch (e: any) {
    ElMessage.error(e?.message || '测试失败')
  }
}

async function deleteEndpoint(ep: WebhookEndpoint) {
  const ok = await confirmRef.value?.open({
    title: '删除端点',
    message: `确定要删除端点「${ep.name}」吗？如果有关联的活跃订阅，将无法删除。`,
    confirmText: '确认删除'
  })
  if (!ok) return
  try {
    await endpointStore.deleteEndpoint(ep.id)
    ElMessage.success('端点已删除')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function copySecret() {
  const ok = await copy(endpointStore.secretOnceVisible || '')
  if (ok) ElMessage.success('已复制到剪贴板')
}

onMounted(() => {
  endpointStore.fetchEndpoints()
  endpointStore.fetchQuota()
})
</script>

<style scoped>
.page-container { padding: 20px; }

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.quota-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}

.quota-text { font-size: 13px; color: #909399; }

.endpoint-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ep-name { font-weight: 500; }

.card-body { margin-bottom: 8px; }

.ep-url {
  font-size: 13px;
  color: #409eff;
  word-break: break-all;
  margin-bottom: 8px;
}

.ep-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
}

.failure-badge {
  color: #f56c6c;
  font-weight: 500;
}

.card-actions {
  display: flex;
  gap: 12px;
  border-top: 1px solid #ebeef5;
  padding-top: 8px;
}

.secret-alert { margin-bottom: 16px; }

.secret-display {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.secret-display code {
  font-size: 13px;
  word-break: break-all;
}
</style>
