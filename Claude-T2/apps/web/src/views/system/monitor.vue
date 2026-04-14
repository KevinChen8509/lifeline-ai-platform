<template>
  <div style="padding: 24px;">
    <a-row :gutter="16">
      <a-col :span="8">
        <a-card title="API服务">
          <a-statistic title="状态" value="UP" value-style="color: #52c41a;" />
          <a-statistic title="运行时间" :value="systemStatus.services?.api?.uptime || 0" suffix="秒" style="margin-top: 16px;" />
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="数据库">
          <a-statistic title="状态" value="UP" value-style="color: #52c41a;" />
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="Redis">
          <a-statistic title="状态" value="UP" value-style="color: #52c41a;" />
        </a-card>
      </a-col>
    </a-row>

    <a-card title="系统资源" style="margin-top: 16px;">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-statistic title="CPU使用率" :value="resources.cpu?.usage || 0" suffix="%" />
          <a-progress :percent="resources.cpu?.usage || 0" :status="(resources.cpu?.warning) ? 'exception' : 'active'" />
          <div style="color: #999; margin-top: 8px;">核心数: {{ resources.cpu?.cores || '-' }}</div>
        </a-col>
        <a-col :span="8">
          <a-statistic title="内存使用率" :value="resources.memory?.usagePercent || 0" suffix="%" />
          <a-progress :percent="resources.memory?.usagePercent || 0" :status="(resources.memory?.warning) ? 'exception' : 'active'" />
          <div style="color: #999; margin-top: 8px;">{{ resources.memory?.used }} / {{ resources.memory?.total }}</div>
        </a-col>
        <a-col :span="8">
          <a-statistic title="运行时间" :value="resources.uptime || 0" suffix="秒" />
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getSystemStatus, getSystemResources } from '@/api/dashboard';

const systemStatus = ref<any>({});
const resources = ref<any>({});

onMounted(async () => {
  try {
    const [statusRes, resRes] = await Promise.all([getSystemStatus(), getSystemResources()]);
    systemStatus.value = statusRes;
    resources.value = resRes;
  } catch {}
});
</script>
