<template>
  <div style="padding: 24px;">
    <a-card title="报告管理">
      <template #extra>
        <a-space>
          <a-button @click="$router.push('/report-templates')">模板管理</a-button>
          <a-button @click="$router.push('/scheduled-reports')">定时报告</a-button>
          <a-button type="primary" @click="showGenerateModal">生成报告</a-button>
        </a-space>
      </template>

      <a-table :columns="columns" :data-source="reports" :loading="loading" row-key="id" :pagination="pagination" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'COMPLETED' ? 'green' : record.status === 'FAILED' ? 'red' : 'blue'">{{ record.status }}</a-tag>
          </template>
          <template v-if="column.key === 'type'">
            <a-tag>{{ record.type }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="record.status === 'COMPLETED'" type="link" size="small" @click="handleExportPdf(record)">导出PDF</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="generateModalVisible" title="生成报告" @ok="handleGenerate">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="报告类型" required>
          <a-select v-model:value="generateForm.type">
            <a-select-option value="DAILY">日报</a-select-option>
            <a-select-option value="WEEKLY">周报</a-select-option>
            <a-select-option value="MONTHLY">月报</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目ID" required>
          <a-input v-model:value="generateForm.projectId" />
        </a-form-item>
        <a-form-item label="时间范围" required>
          <a-range-picker v-model:value="generateForm.dateRange" show-time style="width: 100%" />
        </a-form-item>
        <a-form-item label="模板ID" required>
          <a-input v-model:value="generateForm.templateId" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import type { TableProps } from 'ant-design-vue';
import { listReports, generateReport, deleteReport, exportPdf } from '@/api/report';

const reports = ref<any[]>([]);
const loading = ref(false);
const generateModalVisible = ref(false);
const generateForm = reactive<any>({ type: 'DAILY', projectId: '', dateRange: null, templateId: '' });

const pagination = reactive({ current: 1, pageSize: 20, total: 0, showTotal: (t: number) => `共 ${t} 条` });
const columns = [
  { title: '标题', dataIndex: 'title' },
  { title: '类型', key: 'type', width: 100 },
  { title: '状态', key: 'status', width: 120 },
  { title: '生成时间', dataIndex: 'generatedAt', width: 170 },
  { title: '操作', key: 'action', width: 160 },
];

const loadReports = async () => {
  loading.value = true;
  try {
    const res = await listReports({ page: pagination.current, pageSize: pagination.pageSize });
    reports.value = res.items;
    pagination.total = res.total;
  } catch { message.error('加载失败'); }
  finally { loading.value = false; }
};

const handleTableChange: TableProps['onChange'] = (pag) => { pagination.current = pag.current || 1; loadReports(); };

const showGenerateModal = () => { generateForm.type = 'DAILY'; generateForm.projectId = ''; generateForm.dateRange = null; generateForm.templateId = ''; generateModalVisible.value = true; };
const handleGenerate = async () => {
  try {
    await generateReport({
      type: generateForm.type,
      projectId: generateForm.projectId,
      startDate: generateForm.dateRange?.[0]?.toISOString(),
      endDate: generateForm.dateRange?.[1]?.toISOString(),
      templateId: generateForm.templateId,
    });
    message.success('报告生成中');
    generateModalVisible.value = false;
    loadReports();
  } catch { message.error('生成失败'); }
};

const handleExportPdf = async (record: any) => {
  try { const res = await exportPdf(record.id); message.success('PDF导出成功'); } catch { message.error('导出失败'); }
};

const handleDelete = async (record: any) => {
  try { await deleteReport(record.id); message.success('已删除'); loadReports(); } catch { message.error('删除失败'); }
};

onMounted(() => { loadReports(); });
</script>
