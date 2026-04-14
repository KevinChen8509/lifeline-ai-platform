<template>
  <div style="padding: 24px;">
    <a-card title="遥测数据">
      <div class="filter-bar" style="margin-bottom: 16px; display: flex; gap: 12px; align-items: center;">
        <a-input v-model:value="deviceIdFilter" placeholder="设备ID" style="width: 250px" allow-clear @change="loadData" />
        <a-range-picker v-model:value="timeRange" show-time @change="loadData" />
        <a-button type="primary" @click="loadData">查询</a-button>
      </div>

      <a-table :columns="columns" :data-source="telemetryData" :loading="loading" row-key="id" :pagination="pagination" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'timestamp'">
            {{ new Date(record.timestamp).toLocaleString('zh-CN') }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import type { TableProps } from 'ant-design-vue';
import { getTelemetryList } from '@/api/telemetry';

const telemetryData = ref<any[]>([]);
const loading = ref(false);
const deviceIdFilter = ref('');
const timeRange = ref<any>(null);

const pagination = reactive({ current: 1, pageSize: 20, total: 0, showTotal: (t: number) => `共 ${t} 条` });
const columns = [
  { title: '设备ID', dataIndex: 'deviceId', width: 200 },
  { title: '时间戳', key: 'timestamp', width: 180 },
  { title: '数据', dataIndex: 'metrics', ellipsis: true },
];

const loadData = async () => {
  loading.value = true;
  try {
    const params: any = { page: pagination.current, pageSize: pagination.pageSize };
    if (deviceIdFilter.value) params.deviceId = deviceIdFilter.value;
    if (timeRange.value?.[0]) params.startTime = timeRange.value[0].toISOString();
    if (timeRange.value?.[1]) params.endTime = timeRange.value[1].toISOString();
    const res = await getTelemetryList(params);
    telemetryData.value = res.items;
    pagination.total = res.total;
  } catch {}
  finally { loading.value = false; }
};

const handleTableChange: TableProps['onChange'] = (pag) => { pagination.current = pag.current || 1; loadData(); };

onMounted(() => { loadData(); });
</script>
