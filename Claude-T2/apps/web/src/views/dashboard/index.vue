<template>
  <div class="dashboard-content">
    <a-row :gutter="16">
      <a-col :span="6">
        <a-card title="设备总数" :bordered="false">
          <a-statistic :value="stats.totalDevices" suffix="台">
            <template #prefix>
              <ApiOutlined style="color: #1890ff" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card title="在线设备" :bordered="false">
          <a-statistic :value="stats.onlineDevices" :value-style="{ color: '#3f8600' }" suffix="台">
            <template #prefix>
              <CheckCircleOutlined />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card title="今日预警" :bordered="false">
          <a-statistic :value="stats.todayAlerts" :value-style="{ color: '#cf1322' }" suffix="条">
            <template #prefix>
              <AlertOutlined />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card title="设备在线率" :bordered="false">
          <a-statistic :value="stats.onlineRate" suffix="%">
            <template #prefix>
              <LineChartOutlined style="color: #1890ff" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="系统状态" style="margin-top: 16px" :bordered="false">
      <a-descriptions :column="4">
        <a-descriptions-item label="API服务">
          <a-tag color="success">正常运行</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="数据库">
          <a-tag color="success">正常运行</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="MQTT服务">
          <a-tag color="success">正常运行</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="系统版本">
          <span>v1.0.0</span>
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue';
import {
  ApiOutlined,
  AlertOutlined,
  CheckCircleOutlined,
  LineChartOutlined,
} from '@ant-design/icons-vue';

const stats = reactive({
  totalDevices: 0,
  onlineDevices: 0,
  todayAlerts: 0,
  onlineRate: 0,
});

onMounted(async () => {
  // TODO: fetch from dashboard API
  // Placeholder values for now
  stats.totalDevices = 389;
  stats.onlineDevices = 370;
  stats.todayAlerts = 12;
  stats.onlineRate = 95.1;
});
</script>

<style scoped>
.dashboard-content {
  padding: 16px;
}
</style>
