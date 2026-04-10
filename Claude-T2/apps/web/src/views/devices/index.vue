<template>
  <div class="device-list">
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>设备管理</span>
          <a-tag color="blue">{{ total }} 台设备</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><PlusOutlined /></template>
          添加设备
        </a-button>
      </template>

      <div class="filter-bar">
        <a-input-search
          v-model:value="searchText"
          placeholder="搜索设备名称或SN"
          style="width: 260px"
          @search="handleSearch"
          allow-clear
        />
        <a-select
          v-model:value="statusFilter"
          style="width: 130px; margin-left: 12px"
          placeholder="状态"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option value="online">在线</a-select-option>
          <a-select-option value="offline">离线</a-select-option>
          <a-select-option value="alert">告警</a-select-option>
          <a-select-option value="maintenance">维护中</a-select-option>
          <a-select-option value="pending">待激活</a-select-option>
        </a-select>
        <a-select
          v-model:value="protocolFilter"
          style="width: 130px; margin-left: 12px"
          placeholder="协议"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option value="mqtt">MQTT</a-select-option>
          <a-select-option value="http">HTTP</a-select-option>
          <a-select-option value="modbus_tcp">Modbus TCP</a-select-option>
          <a-select-option value="modbus_rtu">Modbus RTU</a-select-option>
        </a-select>
      </div>

      <a-table
        :columns="columns"
        :data-source="devices"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a @click="viewDetail(record)">{{ record.name }}</a>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColorMap[record.status] || 'default'">
              {{ statusTextMap[record.status] || record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'project'">
            {{ record.project?.name || '-' }}
          </template>
          <template v-else-if="column.key === 'lastSeenAt'">
            {{ record.lastSeenAt ? formatTime(record.lastSeenAt) : '-' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showConfigModal(record)">配置</a-button>
              <a-button type="link" size="small" @click="showHistoryModal(record)">状态历史</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建设备弹窗 -->
    <a-modal
      v-model:open="createModalVisible"
      title="添加设备"
      :confirm-loading="createLoading"
      @ok="handleCreate"
      @cancel="createModalVisible = false"
    >
      <a-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-form-item label="设备名称" name="name">
          <a-input v-model:value="createForm.name" placeholder="请输入设备名称" />
        </a-form-item>
        <a-form-item label="设备SN" name="sn">
          <a-input v-model:value="createForm.sn" placeholder="请输入设备序列号" />
        </a-form-item>
        <a-form-item label="设备来源" name="source">
          <a-select v-model:value="createForm.source" placeholder="选择来源">
            <a-select-option value="self_developed">自研设备</a-select-option>
            <a-select-option value="third_party">第三方设备</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="通信协议" name="protocol">
          <a-select v-model:value="createForm.protocol" placeholder="选择协议">
            <a-select-option value="mqtt">MQTT</a-select-option>
            <a-select-option value="http">HTTP</a-select-option>
            <a-select-option value="modbus_tcp">Modbus TCP</a-select-option>
            <a-select-option value="modbus_rtu">Modbus RTU</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 配置弹窗 -->
    <a-modal
      v-model:open="configModalVisible"
      title="设备配置"
      :confirm-loading="configLoading"
      @ok="handleConfigSave"
    >
      <a-form :label-col="{ span: 8 }" :wrapper-col="{ span: 14 }">
        <a-form-item label="采集间隔(秒)">
          <a-input-number v-model:value="configForm.collectInterval" :min="1" :max="3600" style="width: 100%" />
        </a-form-item>
        <a-form-item label="上传间隔(秒)">
          <a-input-number v-model:value="configForm.uploadInterval" :min="1" :max="86400" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 状态历史弹窗 -->
    <a-modal
      v-model:open="historyModalVisible"
      title="状态变更历史"
      :footer="null"
      width="700px"
    >
      <a-table
        :columns="historyColumns"
        :data-source="statusHistory"
        :loading="historyLoading"
        row-key="id"
        size="small"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fromStatus'">
            <a-tag :color="statusColorMap[record.fromStatus] || 'default'">
              {{ statusTextMap[record.fromStatus] || '-' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'toStatus'">
            <a-tag :color="statusColorMap[record.toStatus] || 'default'">
              {{ statusTextMap[record.toStatus] || record.toStatus }}
            </a-tag>
          </template>
          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import type { TableProps, FormInstance } from 'ant-design-vue';
import {
  getDeviceList,
  createDevice,
  updateDeviceConfig,
  getStatusHistory,
  type Device,
  type CreateDeviceDto,
  type DeviceConfig,
  type StatusHistory,
} from '@/api/device';

const devices = ref<Device[]>([]);
const total = ref(0);
const loading = ref(false);
const searchText = ref('');
const statusFilter = ref<string | undefined>(undefined);
const protocolFilter = ref<string | undefined>(undefined);

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
});

const statusColorMap: Record<string, string> = {
  online: 'green',
  offline: 'default',
  alert: 'red',
  maintenance: 'orange',
  pending: 'blue',
  activating: 'cyan',
  failed: 'red',
};

const statusTextMap: Record<string, string> = {
  online: '在线',
  offline: '离线',
  alert: '告警',
  maintenance: '维护中',
  pending: '待激活',
  activating: '激活中',
  failed: '激活失败',
};

const columns = [
  { title: '设备名称', key: 'name', dataIndex: 'name' },
  { title: 'SN', dataIndex: 'sn', key: 'sn', width: 160 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '项目', key: 'project', width: 150 },
  { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 100 },
  { title: '最后在线', key: 'lastSeenAt', width: 170 },
  { title: '操作', key: 'action', width: 180 },
];

// Create modal
const createModalVisible = ref(false);
const createLoading = ref(false);
const createFormRef = ref<FormInstance>();
const createForm = reactive<CreateDeviceDto>({
  name: '',
  sn: '',
  source: 'self_developed',
  protocol: 'mqtt',
});

const createRules = {
  name: [{ required: true, message: '请输入设备名称' }],
  sn: [{ required: true, message: '请输入设备序列号' }],
  source: [{ required: true, message: '请选择设备来源' }],
  protocol: [{ required: true, message: '请选择通信协议' }],
};

// Config modal
const configModalVisible = ref(false);
const configLoading = ref(false);
const configDeviceId = ref('');
const configForm = reactive<DeviceConfig>({
  collectInterval: 10,
  uploadInterval: 60,
});

// History modal
const historyModalVisible = ref(false);
const historyLoading = ref(false);
const statusHistory = ref<StatusHistory[]>([]);

const historyColumns = [
  { title: '原状态', key: 'fromStatus', width: 100 },
  { title: '新状态', key: 'toStatus', width: 100 },
  { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
  { title: '时间', key: 'createdAt', width: 170 },
];

function formatTime(time: string): string {
  return new Date(time).toLocaleString('zh-CN');
}

const loadDevices = async () => {
  loading.value = true;
  try {
    const res = await getDeviceList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      search: searchText.value || undefined,
      status: statusFilter.value,
      protocol: protocolFilter.value,
    });
    devices.value = res.items;
    total.value = res.total;
    pagination.total = res.total;
  } catch {
    message.error('加载设备列表失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.current = 1;
  loadDevices();
};

const handleFilterChange = () => {
  pagination.current = 1;
  loadDevices();
};

const handleTableChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current || 1;
  pagination.pageSize = pag.pageSize || 20;
  loadDevices();
};

const viewDetail = (record: Device) => {
  // TODO: navigate to device detail
  console.log('view detail', record.id);
};

const showCreateModal = () => {
  createForm.name = '';
  createForm.sn = '';
  createForm.source = 'self_developed';
  createForm.protocol = 'mqtt';
  createModalVisible.value = true;
};

const handleCreate = async () => {
  try {
    await createFormRef.value?.validate();
    createLoading.value = true;
    await createDevice(createForm);
    message.success('设备创建成功');
    createModalVisible.value = false;
    loadDevices();
  } catch {
    // validation or API error
  } finally {
    createLoading.value = false;
  }
};

const showConfigModal = async (device: Device) => {
  configDeviceId.value = device.id;
  configForm.collectInterval = device.config?.collectInterval || 10;
  configForm.uploadInterval = device.config?.uploadInterval || 60;
  configModalVisible.value = true;
};

const handleConfigSave = async () => {
  configLoading.value = true;
  try {
    await updateDeviceConfig(configDeviceId.value, { ...configForm });
    message.success('配置更新成功');
    configModalVisible.value = false;
    loadDevices();
  } catch {
    message.error('配置更新失败');
  } finally {
    configLoading.value = false;
  }
};

const showHistoryModal = async (device: Device) => {
  historyModalVisible.value = true;
  historyLoading.value = true;
  try {
    const res = await getStatusHistory(device.id);
    statusHistory.value = res.items;
  } catch {
    message.error('加载状态历史失败');
  } finally {
    historyLoading.value = false;
  }
};

onMounted(() => {
  loadDevices();
});
</script>

<style scoped>
.device-list {
  padding: 24px;
}

.filter-bar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
</style>
