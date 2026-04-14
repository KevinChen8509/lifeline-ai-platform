<template>
  <div style="padding: 24px;">
    <a-card title="定时报告">
      <template #extra>
        <a-button type="primary" @click="showCreateModal">创建定时报告</a-button>
      </template>

      <a-table :columns="columns" :data-source="scheduledReports" :loading="loading" row-key="id" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag>{{ record.type }}</a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'orange'">{{ record.status }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleExecute(record)">立即执行</a-button>
              <a-button type="link" size="small" @click="handleToggle(record)">{{ record.status === 'ACTIVE' ? '暂停' : '激活' }}</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" title="创建定时报告" @ok="handleCreate">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="报告类型" required>
          <a-select v-model:value="form.type">
            <a-select-option value="DAILY">日报</a-select-option>
            <a-select-option value="WEEKLY">周报</a-select-option>
            <a-select-option value="MONTHLY">月报</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="模板ID" required>
          <a-input v-model:value="form.templateId" />
        </a-form-item>
        <a-form-item label="项目IDs">
          <a-input v-model:value="form.projectIds" placeholder='["projectId1", "projectId2"]' />
        </a-form-item>
        <a-form-item label="收件人">
          <a-input v-model:value="form.recipients" placeholder='["email@example.com"]' />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listScheduledReports, createScheduledReport, updateScheduledReport, deleteScheduledReport, executeScheduledReport } from '@/api/report';

const scheduledReports = ref<any[]>([]);
const loading = ref(false);
const modalVisible = ref(false);
const form = reactive({ name: '', type: 'DAILY', templateId: '', projectIds: '[]', recipients: '[]' });

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '类型', key: 'type', width: 80 },
  { title: 'Cron', dataIndex: 'cron', width: 130 },
  { title: '状态', key: 'status', width: 80 },
  { title: '上次执行', dataIndex: 'lastRunAt', width: 170 },
  { title: '操作', key: 'action', width: 220 },
];

const loadData = async () => { loading.value = true; try { scheduledReports.value = await listScheduledReports(); } finally { loading.value = false; } };

const showCreateModal = () => { form.name = ''; form.type = 'DAILY'; form.templateId = ''; form.projectIds = '[]'; form.recipients = '[]'; modalVisible.value = true; };
const handleCreate = async () => {
  try { await createScheduledReport(form); message.success('创建成功'); modalVisible.value = false; loadData(); } catch { message.error('创建失败'); }
};

const handleExecute = async (record: any) => {
  try { await executeScheduledReport(record.id); message.success('执行已触发'); loadData(); } catch { message.error('执行失败'); }
};

const handleToggle = async (record: any) => {
  try {
    await updateScheduledReport(record.id, { status: record.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' });
    message.success('状态已更新');
    loadData();
  } catch { message.error('操作失败'); }
};

const handleDelete = async (record: any) => {
  try { await deleteScheduledReport(record.id); message.success('已删除'); loadData(); } catch { message.error('删除失败'); }
};

onMounted(() => { loadData(); });
</script>
