<template>
  <div style="padding: 24px;">
    <a-row :gutter="16">
      <a-col :span="12">
        <a-card title="预警类型分布">
          <div style="height: 300px; display: flex; align-items: center; justify-content: center; color: #999;">
            <div id="type-chart">预警类型分布图</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="预警处理效率">
          <a-descriptions :column="1" bordered size="small">
            <a-descriptions-item label="平均响应时间">{{ efficiency.avgResponseTime || 0 }} 分钟</a-descriptions-item>
            <a-descriptions-item label="平均处理时间">{{ efficiency.avgHandleTime || 0 }} 分钟</a-descriptions-item>
            <a-descriptions-item label="及时率">{{ efficiency.timelinessRate || 0 }}%</a-descriptions-item>
          </a-descriptions>
          <a-divider />
          <a-descriptions title="SLA阈值" :column="2" size="small">
            <a-descriptions-item label="紧急">4小时</a-descriptions-item>
            <a-descriptions-item label="高">8小时</a-descriptions-item>
            <a-descriptions-item label="中">24小时</a-descriptions-item>
            <a-descriptions-item label="低">72小时</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getAlertTypeDistribution, getAlertHandlingEfficiency } from '@/api/dashboard';

const efficiency = ref<any>({});

onMounted(async () => {
  try {
    const [distRes, effRes] = await Promise.all([
      getAlertTypeDistribution(),
      getAlertHandlingEfficiency(),
    ]);
    efficiency.value = effRes;
  } catch {}
});
</script>
