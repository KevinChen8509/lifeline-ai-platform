<template>
  <div style="padding: 24px;">
    <a-card title="预警详情">
      <template #extra>
        <a-button @click="$router.back()">返回</a-button>
      </template>

      <a-descriptions :column="2" bordered size="small" v-if="alert">
        <a-descriptions-item label="ID">{{ alert.id }}</a-descriptions-item>
        <a-descriptions-item label="标题">{{ alert.title }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ alert.type }}</a-descriptions-item>
        <a-descriptions-item label="级别">
          <a-tag :color="levelColorMap[alert.level]">{{ alert.level }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="statusColorMap[alert.status]">{{ alert.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="置信度">{{ (alert.confidence * 100).toFixed(1) }}%</a-descriptions-item>
        <a-descriptions-item label="设备ID">{{ alert.deviceId }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ formatTime(alert.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="内容" :span="2">{{ alert.content }}</a-descriptions-item>
      </a-descriptions>

      <div style="margin-top: 16px;" v-if="alert">
        <a-space v-if="alert.status === 'pending'">
          <a-button type="primary" @click="handleAcknowledge">确认预警</a-button>
        </a-space>
        <a-space v-if="alert.status === 'acknowledged'">
          <a-button type="primary" @click="handleProcess">开始处置</a-button>
        </a-space>
        <a-space v-if="alert.status === 'in_progress'">
          <a-button @click="showCloseModal">关闭预警</a-button>
          <a-button type="primary" @click="showWorkOrderModal">创建工单</a-button>
        </a-space>
      </div>
    </a-card>

    <a-tabs v-model:activeKey="activeTab" style="margin-top: 16px;">
      <a-tab-pane key="timeline" tab="处理时间线">
        <div v-if="timeline" style="margin-top: 16px;">
          <a-alert v-if="timeline.isOverdue" type="error" message="预警已超时" style="margin-bottom: 16px;" show-icon />
          <a-timeline>
            <a-timeline-item v-for="node in timeline.nodes" :key="node.id" :color="nodeColor(node.newStatus)">
              <p><a-tag>{{ statusTextMap[node.oldStatus] || '-' }}</a-tag> → <a-tag :color="statusColorMap[node.newStatus]">{{ statusTextMap[node.newStatus] }}</a-tag></p>
              <p style="color: #999; font-size: 12px;">{{ formatTime(node.createdAt) }} {{ node.note ? '- ' + node.note : '' }}</p>
            </a-timeline-item>
          </a-timeline>
          <div style="text-align: center;">
            <a-progress :percent="timeline.progress" :stroke-color="timeline.isOverdue ? '#ff4d4f' : '#1890ff'" />
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="workOrders" tab="工单">
        <a-table :columns="woColumns" :data-source="timeline?.workOrders || []" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag>{{ record.status }}</a-tag>
            </template>
            <template v-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- Close Modal -->
    <a-modal v-model:open="closeModalVisible" title="关闭预警" @ok="handleClose">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="处理结果" required>
          <a-textarea v-model:value="closeResolution" :rows="3" />
        </a-form-item>
        <a-form-item label="根因分析">
          <a-textarea v-model:value="closeRootCause" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Work Order Modal -->
    <a-modal v-model:open="woModalVisible" title="创建工单" @ok="handleCreateWorkOrder">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="标题" required>
          <a-input v-model:value="woForm.title" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="woForm.description" :rows="3" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-select v-model:value="woForm.priority">
            <a-select-option value="critical">紧急</a-select-option>
            <a-select-option value="high">高</a-select-option>
            <a-select-option value="medium">中</a-select-option>
            <a-select-option value="low">低</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { getAlert, acknowledgeAlert, processAlert, closeAlert, getAlertTimeline, createWorkOrder } from '@/api/alert';

const route = useRoute();
const alertId = route.params.id as string;

const alert = ref<any>(null);
const timeline = ref<any>(null);
const activeTab = ref('timeline');
const closeModalVisible = ref(false);
const closeResolution = ref('');
const closeRootCause = ref('');
const woModalVisible = ref(false);
const woForm = ref({ title: '', description: '', priority: 'medium' });

const levelColorMap: Record<string, string> = { critical: 'red', high: 'orange', medium: 'gold', low: 'blue' };
const statusColorMap: Record<string, string> = { pending: 'blue', acknowledged: 'cyan', in_progress: 'orange', resolved: 'green', closed: 'default' };
const statusTextMap: Record<string, string> = { pending: '待处理', acknowledged: '已确认', in_progress: '处理中', resolved: '已解决', closed: '已关闭' };

const woColumns = [
  { title: '工单号', dataIndex: 'workOrderNo', width: 180 },
  { title: '标题', dataIndex: 'title' },
  { title: '状态', key: 'status', width: 100 },
  { title: '优先级', dataIndex: 'priority', width: 80 },
  { title: '创建时间', key: 'createdAt', width: 170 },
];

function formatTime(time: string) { return new Date(time).toLocaleString('zh-CN'); }
function nodeColor(status: string) { return statusColorMap[status] || 'blue'; }

const loadAlert = async () => {
  try { alert.value = await getAlert(alertId); } catch { message.error('加载预警失败'); }
};

const loadTimeline = async () => {
  try { timeline.value = await getAlertTimeline(alertId); } catch { message.error('加载时间线失败'); }
};

const handleAcknowledge = async () => {
  try { await acknowledgeAlert(alertId); message.success('已确认'); loadAlert(); loadTimeline(); } catch { message.error('操作失败'); }
};

const handleProcess = async () => {
  try { await processAlert(alertId, '开始处置'); message.success('已开始处置'); loadAlert(); loadTimeline(); } catch { message.error('操作失败'); }
};

const showCloseModal = () => { closeResolution.value = ''; closeRootCause.value = ''; closeModalVisible.value = true; };
const handleClose = async () => {
  try { await closeAlert(alertId, closeResolution.value, closeRootCause.value); message.success('已关闭'); closeModalVisible.value = false; loadAlert(); loadTimeline(); } catch { message.error('关闭失败'); }
};

const showWorkOrderModal = () => { woForm.value = { title: '', description: '', priority: alert.value?.level || 'medium' }; woModalVisible.value = true; };
const handleCreateWorkOrder = async () => {
  try { await createWorkOrder(alertId, woForm.value); message.success('工单已创建'); woModalVisible.value = false; loadTimeline(); } catch { message.error('创建工单失败'); }
};

onMounted(() => { loadAlert(); loadTimeline(); });
</script>
