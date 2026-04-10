<template>
  <div class="alert-list">
    <!-- Stats cards -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="严重" :value="stats.critical" :value-style="{ color: '#cf1322' }" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="严重" :value="stats.high" :value-style="{ color: '#fa8c16' }" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="较重" :value="stats.medium" :value-style="{ color: '#fadb14' }" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="一般" :value="stats.low" />
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>预警管理</span>
          <a-tag color="red">{{ stats.unacknowledged }} 条待处理</a-tag>
        </a-space>
      </template>

      <div class="filter-bar">
        <a-select
          v-model:value="levelFilter"
          style="width: 120px"
          placeholder="级别"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option value="critical">严重</a-select-option>
          <a-select-option value="high">严重</a-select-option>
          <a-select-option value="medium">较重</a-select-option>
          <a-select-option value="low">一般</a-select-option>
        </a-select>
        <a-select
          v-model:value="statusFilter"
          style="width: 120px; margin-left: 12px"
          placeholder="状态"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option value="pending">待处理</a-select-option>
          <a-select-option value="acknowledged">已确认</a-select-option>
          <a-select-option value="in_progress">处置中</a-select-option>
          <a-select-option value="resolved">已解决</a-select-option>
          <a-select-option value="closed">已关闭</a-select-option>
        </a-select>
        <a-select
          v-model:value="typeFilter"
          style="width: 130px; margin-left: 12px"
          placeholder="类型"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option value="mixed_connection">错混接</a-select-option>
          <a-select-option value="silt">淤堵</a-select-option>
          <a-select-option value="overflow">溢流</a-select-option>
          <a-select-option value="full_pipe">满管</a-select-option>
          <a-select-option value="threshold_exceeded">阈值超限</a-select-option>
        </a-select>
        <a-input-search
          v-model:value="searchText"
          placeholder="搜索预警标题"
          style="width: 220px; margin-left: 12px"
          @search="handleSearch"
          allow-clear
        />
      </div>

      <a-table
        :columns="columns"
        :data-source="alerts"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'level'">
            <a-tag :color="levelColorMap[record.level]">{{ levelTextMap[record.level] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="alertStatusColorMap[record.status]">{{ alertStatusTextMap[record.status] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'type'">
            {{ typeTextMap[record.type] || record.type }}
          </template>
          <template v-else-if="column.key === 'device'">
            {{ record.device?.name || '-' }}
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button
                v-if="record.status === 'pending'"
                type="link"
                size="small"
                @click="handleAcknowledge(record)"
              >
                确认
              </a-button>
              <a-button
                v-if="record.status === 'acknowledged'"
                type="link"
                size="small"
                @click="showProcessModal(record)"
              >
                处置
              </a-button>
              <a-button
                v-if="record.status === 'in_progress'"
                type="link"
                size="small"
                @click="showCloseModal(record)"
              >
                关闭
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 处置弹窗 -->
    <a-modal
      v-model:open="processModalVisible"
      title="开始处置"
      :confirm-loading="actionLoading"
      @ok="handleProcess"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="处置说明" required>
          <a-textarea v-model:value="processDescription" :rows="3" placeholder="请输入处置说明" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 关闭弹窗 -->
    <a-modal
      v-model:open="closeModalVisible"
      title="关闭预警"
      :confirm-loading="actionLoading"
      @ok="handleClose"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="处置结果" required>
          <a-textarea v-model:value="closeForm.resolution" :rows="3" placeholder="请输入处置结果" />
        </a-form-item>
        <a-form-item label="根本原因">
          <a-input v-model:value="closeForm.rootCause" placeholder="可选" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { TableProps } from 'ant-design-vue';
import {
  getAlertList,
  getAlertStats,
  acknowledgeAlert,
  processAlert,
  closeAlert,
  type Alert,
  type AlertStats,
  type AlertLevel,
} from '@/api/alert';

const alerts = ref<Alert[]>([]);
const loading = ref(false);
const searchText = ref('');
const levelFilter = ref<string | undefined>(undefined);
const statusFilter = ref<string | undefined>(undefined);
const typeFilter = ref<string | undefined>(undefined);

const stats = ref<AlertStats>({ critical: 0, high: 0, medium: 0, low: 0, unacknowledged: 0 });

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
});

const levelColorMap: Record<string, string> = {
  critical: 'red',
  high: 'orange',
  medium: 'gold',
  low: 'blue',
};
const levelTextMap: Record<string, string> = { critical: '严重', high: '严重', medium: '较重', low: '一般' };

const alertStatusColorMap: Record<string, string> = {
  pending: 'red',
  acknowledged: 'orange',
  in_progress: 'blue',
  resolved: 'cyan',
  closed: 'default',
};
const alertStatusTextMap: Record<string, string> = {
  pending: '待处理',
  acknowledged: '已确认',
  in_progress: '处置中',
  resolved: '已解决',
  closed: '已关闭',
};

const typeTextMap: Record<string, string> = {
  mixed_connection: '错混接',
  silt: '淤堵',
  overflow: '溢流',
  full_pipe: '满管',
  threshold_exceeded: '阈值超限',
};

const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '类型', key: 'type', dataIndex: 'type', width: 100 },
  { title: '级别', key: 'level', dataIndex: 'level', width: 80 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 90 },
  { title: '设备', key: 'device', width: 130 },
  { title: '时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 160 },
];

// Action modals
const processModalVisible = ref(false);
const closeModalVisible = ref(false);
const actionLoading = ref(false);
const currentAlert = ref<Alert | null>(null);
const processDescription = ref('');
const closeForm = reactive({ resolution: '', rootCause: '' });

function formatTime(time: string): string {
  return new Date(time).toLocaleString('zh-CN');
}

const loadAlerts = async () => {
  loading.value = true;
  try {
    const res = await getAlertList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      level: levelFilter.value as AlertLevel | undefined,
      status: statusFilter.value as any,
      type: typeFilter.value as any,
      search: searchText.value || undefined,
    });
    alerts.value = res.items;
    pagination.total = res.total;
  } catch {
    message.error('加载预警列表失败');
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    stats.value = await getAlertStats();
  } catch {
    // ignore
  }
};

const handleSearch = () => {
  pagination.current = 1;
  loadAlerts();
};

const handleFilterChange = () => {
  pagination.current = 1;
  loadAlerts();
};

const handleTableChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current || 1;
  pagination.pageSize = pag.pageSize || 20;
  loadAlerts();
};

const handleAcknowledge = (alert: Alert) => {
  Modal.confirm({
    title: '确认预警',
    content: `确定要确认预警 "${alert.title}" 吗？`,
    onOk: async () => {
      try {
        await acknowledgeAlert(alert.id);
        message.success('预警已确认');
        loadAlerts();
        loadStats();
      } catch {
        message.error('确认失败');
      }
    },
  });
};

const showProcessModal = (alert: Alert) => {
  currentAlert.value = alert;
  processDescription.value = '';
  processModalVisible.value = true;
};

const handleProcess = async () => {
  if (!processDescription.value) {
    message.warning('请输入处置说明');
    return;
  }
  actionLoading.value = true;
  try {
    await processAlert(currentAlert.value!.id, processDescription.value);
    message.success('开始处置');
    processModalVisible.value = false;
    loadAlerts();
  } catch {
    message.error('处置失败');
  } finally {
    actionLoading.value = false;
  }
};

const showCloseModal = (alert: Alert) => {
  currentAlert.value = alert;
  closeForm.resolution = '';
  closeForm.rootCause = '';
  closeModalVisible.value = true;
};

const handleClose = async () => {
  if (!closeForm.resolution) {
    message.warning('请输入处置结果');
    return;
  }
  actionLoading.value = true;
  try {
    await closeAlert(currentAlert.value!.id, closeForm.resolution, closeForm.rootCause || undefined);
    message.success('预警已关闭');
    closeModalVisible.value = false;
    loadAlerts();
    loadStats();
  } catch {
    message.error('关闭失败');
  } finally {
    actionLoading.value = false;
  }
};

onMounted(() => {
  loadAlerts();
  loadStats();
});
</script>

<style scoped>
.alert-list {
  padding: 24px;
}

.filter-bar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
</style>
