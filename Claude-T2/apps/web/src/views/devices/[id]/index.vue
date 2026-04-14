<template>
  <div class="device-detail" style="padding: 24px;">
    <a-page-header :title="device?.name || '设备详情'" @back="$router.back()">
      <template #tags>
        <a-tag :color="statusColorMap[device?.status] || 'default'">
          {{ statusTextMap[device?.status] || device?.status }}
        </a-tag>
      </template>
      <template #extra>
        <a-space>
          <a-button v-if="device?.status === 'pending' || device?.status === 'failed'" type="primary" @click="handleActivate">激活设备</a-button>
          <a-button v-if="device?.status === 'online'" @click="showOtaModal">OTA升级</a-button>
          <a-button @click="showAssignModal">分配项目</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="info" tab="基本信息">
        <a-descriptions :column="2" bordered size="small" v-if="device">
          <a-descriptions-item label="ID">{{ device.id }}</a-descriptions-item>
          <a-descriptions-item label="序列号">{{ device.serialNumber }}</a-descriptions-item>
          <a-descriptions-item label="设备类型">{{ device.deviceType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="厂商">{{ device.manufacturer || '-' }}</a-descriptions-item>
          <a-descriptions-item label="固件版本">{{ device.firmwareVersion || '-' }}</a-descriptions-item>
          <a-descriptions-item label="通信协议">{{ device.protocol || '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ device.source === 'self_developed' ? '自研' : '第三方' }}</a-descriptions-item>
          <a-descriptions-item label="最后在线">{{ device.lastOnlineAt ? formatTime(device.lastOnlineAt) : '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(device.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ formatTime(device.updatedAt) }}</a-descriptions-item>
          <a-descriptions-item label="描述" :span="2">{{ device.description || '-' }}</a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>

      <a-tab-pane key="history" tab="状态历史">
        <a-table :columns="historyColumns" :data-source="statusHistory" :loading="historyLoading" row-key="id" size="small" :pagination="{ pageSize: 10 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fromStatus'">
              <a-tag :color="statusColorMap[record.fromStatus] || 'default'">{{ statusTextMap[record.fromStatus] || '-' }}</a-tag>
            </template>
            <template v-if="column.key === 'toStatus'">
              <a-tag :color="statusColorMap[record.toStatus] || 'default'">{{ statusTextMap[record.toStatus] }}</a-tag>
            </template>
            <template v-if="column.key === 'timestamp'">
              {{ formatTime(record.timestamp) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="models" tab="模型绑定">
        <div style="margin-bottom: 16px;">
          <a-button type="primary" @click="showBindModelModal">绑定模型</a-button>
        </div>
        <a-table :columns="bindingColumns" :data-source="bindings" :loading="bindingLoading" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 'running' ? 'green' : record.status === 'error' ? 'red' : 'blue'">{{ record.status }}</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-popconfirm title="确定解绑?" @confirm="handleUnbind(record)">
                <a-button type="link" danger size="small">解绑</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="telemetry" tab="遥测数据">
        <div style="margin-bottom: 16px; display: flex; gap: 12px; align-items: center;">
          <a-range-picker v-model:value="timeRange" show-time @change="loadTelemetry" />
          <a-select v-model:value="chartInterval" style="width: 120px" @change="loadTelemetry">
            <a-select-option value="raw">原始数据</a-select-option>
            <a-select-option value="hour">按小时</a-select-option>
            <a-select-option value="day">按天</a-select-option>
          </a-select>
        </div>
        <a-table :columns="telemetryColumns" :data-source="telemetryData" :loading="telemetryLoading" row-key="id" size="small" :pagination="{ pageSize: 10 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'timestamp'">
              {{ formatTime(record.timestamp) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- OTA Modal -->
    <a-modal v-model:open="otaModalVisible" title="OTA升级" @ok="handleOta">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="目标版本">
          <a-input v-model:value="otaTargetVersion" placeholder="输入目标固件版本" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Assign Project Modal -->
    <a-modal v-model:open="assignModalVisible" title="分配项目" @ok="handleAssign">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="项目ID">
          <a-input v-model:value="assignProjectId" placeholder="输入项目ID (留空取消分配)" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Bind Model Modal -->
    <a-modal v-model:open="bindModelModalVisible" title="绑定模型" @ok="handleBindModels">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="模型ID列表">
          <a-select v-model:value="bindModelIds" mode="tags" placeholder="输入模型ID" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  getDevice, getStatusHistory, getBoundModels, activateDevice, otaUpgrade,
  assignProject, bindModels, unbindModel,
} from '@/api/device';
import { getDeviceTelemetry } from '@/api/telemetry';

const route = useRoute();
const deviceId = route.params.id as string;

const device = ref<any>(null);
const activeTab = ref('info');
const statusHistory = ref<any[]>([]);
const historyLoading = ref(false);
const bindings = ref<any[]>([]);
const bindingLoading = ref(false);
const telemetryData = ref<any[]>([]);
const telemetryLoading = ref(false);
const timeRange = ref<any>(null);
const chartInterval = ref('raw');

const otaModalVisible = ref(false);
const otaTargetVersion = ref('');
const assignModalVisible = ref(false);
const assignProjectId = ref('');
const bindModelModalVisible = ref(false);
const bindModelIds = ref<string[]>([]);

const statusColorMap: Record<string, string> = {
  online: 'green', offline: 'default', alert: 'red', maintenance: 'orange',
  pending: 'blue', activating: 'cyan', failed: 'red',
};
const statusTextMap: Record<string, string> = {
  online: '在线', offline: '离线', alert: '告警', maintenance: '维护中',
  pending: '待激活', activating: '激活中', failed: '激活失败',
};

const historyColumns = [
  { title: '原状态', key: 'fromStatus', width: 100 },
  { title: '新状态', key: 'toStatus', width: 100 },
  { title: '原因', dataIndex: 'reason', ellipsis: true },
  { title: '时间', key: 'timestamp', width: 170 },
];

const bindingColumns = [
  { title: '模型ID', dataIndex: 'modelId', width: 200 },
  { title: '版本', dataIndex: 'boundVersion', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '绑定时间', dataIndex: 'boundAt', width: 170 },
  { title: '操作', key: 'action', width: 80 },
];

const telemetryColumns = [
  { title: '时间戳', key: 'timestamp', width: 180 },
  { title: '数据', dataIndex: 'metrics', ellipsis: true },
];

function formatTime(time: string) {
  return new Date(time).toLocaleString('zh-CN');
}

const loadDevice = async () => {
  try {
    device.value = await getDevice(deviceId);
  } catch { message.error('加载设备失败'); }
};

const loadHistory = async () => {
  historyLoading.value = true;
  try {
    const res = await getStatusHistory(deviceId, { pageSize: 50 });
    statusHistory.value = res.items;
  } catch { message.error('加载状态历史失败'); }
  finally { historyLoading.value = false; }
};

const loadBindings = async () => {
  bindingLoading.value = true;
  try {
    const res = await getBoundModels(deviceId, { pageSize: 50 });
    bindings.value = res.items;
  } catch { message.error('加载模型绑定失败'); }
  finally { bindingLoading.value = false; }
};

const loadTelemetry = async () => {
  telemetryLoading.value = true;
  try {
    const params: any = { pageSize: 50 };
    if (timeRange.value?.[0]) params.startTime = timeRange.value[0].toISOString();
    if (timeRange.value?.[1]) params.endTime = timeRange.value[1].toISOString();
    const res = await getDeviceTelemetry(deviceId, params);
    telemetryData.value = res.items;
  } catch { message.error('加载遥测数据失败'); }
  finally { telemetryLoading.value = false; }
};

const handleActivate = async () => {
  try {
    await activateDevice(deviceId);
    message.success('激活命令已发送');
    loadDevice();
  } catch { message.error('激活失败'); }
};

const showOtaModal = () => { otaTargetVersion.value = ''; otaModalVisible.value = true; };
const handleOta = async () => {
  try {
    await otaUpgrade(deviceId, otaTargetVersion.value);
    message.success('OTA升级命令已发送');
    otaModalVisible.value = false;
  } catch { message.error('OTA升级失败'); }
};

const showAssignModal = () => { assignProjectId.value = ''; assignModalVisible.value = true; };
const handleAssign = async () => {
  try {
    await assignProject(deviceId, assignProjectId.value || null);
    message.success('项目分配成功');
    assignModalVisible.value = false;
    loadDevice();
  } catch { message.error('项目分配失败'); }
};

const showBindModelModal = () => { bindModelIds.value = []; bindModelModalVisible.value = true; };
const handleBindModels = async () => {
  try {
    await bindModels(deviceId, bindModelIds.value);
    message.success('模型绑定成功');
    bindModelModalVisible.value = false;
    loadBindings();
  } catch { message.error('模型绑定失败'); }
};

const handleUnbind = async (record: any) => {
  try {
    await unbindModel(deviceId, record.modelId);
    message.success('模型解绑成功');
    loadBindings();
  } catch { message.error('模型解绑失败'); }
};

onMounted(() => {
  loadDevice();
  loadHistory();
  loadBindings();
  loadTelemetry();
});
</script>
