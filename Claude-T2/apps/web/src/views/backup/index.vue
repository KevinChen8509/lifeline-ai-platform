<template>
  <div style="padding: 24px;">
    <a-card title="数据备份">
      <template #extra>
        <a-button type="primary" @click="showCreateConfigModal">创建备份配置</a-button>
      </template>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="configs" tab="备份配置">
          <a-table :columns="configColumns" :data-source="configs" :loading="configLoading" row-key="id" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'type'">
                <a-tag>{{ record.type }}</a-tag>
              </template>
              <template v-if="column.key === 'isEnabled'">
                <a-tag :color="record.isEnabled ? 'green' : 'red'">{{ record.isEnabled ? '启用' : '禁用' }}</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-button type="primary" size="small" @click="handleExecute(record)">执行备份</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="logs" tab="备份日志">
          <div style="margin-bottom: 16px; display: flex; gap: 12px;">
            <a-select v-model:value="logTypeFilter" style="width: 130px" placeholder="类型" allow-clear @change="loadLogs">
              <a-select-option value="incremental">增量</a-select-option>
              <a-select-option value="full">全量</a-select-option>
            </a-select>
            <a-select v-model:value="logStatusFilter" style="width: 130px" placeholder="状态" allow-clear @change="loadLogs">
              <a-select-option value="completed">完成</a-select-option>
              <a-select-option value="failed">失败</a-select-option>
              <a-select-option value="running">运行中</a-select-option>
            </a-select>
          </div>
          <a-table :columns="logColumns" :data-source="logs" :loading="logLoading" row-key="id" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="record.status === 'completed' ? 'green' : record.status === 'failed' ? 'red' : 'blue'">{{ record.status }}</a-tag>
              </template>
              <template v-if="column.key === 'fileSize'">
                {{ record.fileSize ? (record.fileSize / 1024 / 1024).toFixed(1) + 'MB' : '-' }}
              </template>
              <template v-if="column.key === 'action'">
                <a-button v-if="record.status === 'completed'" type="link" size="small" @click="handleRestore(record)">恢复</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-modal v-model:open="configModalVisible" title="创建备份配置" @ok="handleCreateConfig">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="备份类型" required>
          <a-select v-model:value="configForm.type">
            <a-select-option value="incremental">增量</a-select-option>
            <a-select-option value="full">全量</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Cron表达式">
          <a-input v-model:value="configForm.schedule" placeholder="0 2 * * *" />
        </a-form-item>
        <a-form-item label="保留天数">
          <a-input-number v-model:value="configForm.retentionDays" :min="1" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listBackupConfigs, createBackupConfig, executeBackup, listBackupLogs, restoreBackup } from '@/api/telemetry';

const activeTab = ref('configs');
const configs = ref<any[]>([]);
const configLoading = ref(false);
const logs = ref<any[]>([]);
const logLoading = ref(false);
const logTypeFilter = ref<string | undefined>();
const logStatusFilter = ref<string | undefined>();
const configModalVisible = ref(false);
const configForm = reactive({ type: 'full' as string, schedule: '0 2 * * *', retentionDays: 30 });

const configColumns = [
  { title: '类型', key: 'type', width: 100 },
  { title: '调度', dataIndex: 'schedule', width: 150 },
  { title: '保留天数', dataIndex: 'retentionDays', width: 100 },
  { title: '启用', key: 'isEnabled', width: 80 },
  { title: '操作', key: 'action', width: 120 },
];

const logColumns = [
  { title: '类型', dataIndex: 'type', width: 80 },
  { title: '状态', key: 'status', width: 100 },
  { title: '文件大小', key: 'fileSize', width: 100 },
  { title: '耗时(秒)', dataIndex: 'duration', width: 100 },
  { title: '错误', dataIndex: 'error', ellipsis: true },
  { title: '操作', key: 'action', width: 80 },
];

const loadConfigs = async () => { configLoading.value = true; try { configs.value = await listBackupConfigs(); } finally { configLoading.value = false; } };
const loadLogs = async () => { logLoading.value = true; try { logs.value = await listBackupLogs({ type: logTypeFilter.value, status: logStatusFilter.value }); } finally { logLoading.value = false; } };

const showCreateConfigModal = () => { configForm.type = 'full'; configForm.schedule = '0 2 * * *'; configForm.retentionDays = 30; configModalVisible.value = true; };
const handleCreateConfig = async () => {
  try { await createBackupConfig(configForm); message.success('配置已创建'); configModalVisible.value = false; loadConfigs(); } catch { message.error('创建失败'); }
};
const handleExecute = async (record: any) => {
  try { await executeBackup(record.id); message.success('备份已执行'); loadLogs(); } catch { message.error('执行失败'); }
};
const handleRestore = async (record: any) => {
  try { await restoreBackup(record.id); message.success('恢复已触发'); } catch { message.error('恢复失败'); }
};

onMounted(() => { loadConfigs(); loadLogs(); });
</script>
