<template>
  <div style="padding: 24px;">
    <a-card title="模型详情">
      <template #extra>
        <a-space>
          <a-button v-if="model?.status === 'draft'" type="primary" @click="handlePublish">发布</a-button>
          <a-button v-if="model?.status === 'published'" @click="handleDeprecate">废弃</a-button>
          <a-button @click="$router.back()">返回</a-button>
        </a-space>
      </template>

      <a-descriptions :column="2" bordered size="small" v-if="model">
        <a-descriptions-item label="名称">{{ model.name }}</a-descriptions-item>
        <a-descriptions-item label="代码">{{ model.code }}</a-descriptions-item>
        <a-descriptions-item label="版本">{{ model.version }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ model.type }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="model.status === 'published' ? 'green' : model.status === 'deprecated' ? 'red' : 'blue'">{{ model.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">{{ model.description || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-tabs v-model:activeKey="activeTab" style="margin-top: 16px;">
      <a-tab-pane key="versions" tab="版本管理">
        <div style="margin-bottom: 16px;">
          <a-button type="primary" @click="showCreateVersionModal">创建版本</a-button>
        </div>
        <a-table :columns="versionColumns" :data-source="versions" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 'published' ? 'green' : record.status === 'deprecated' ? 'red' : 'blue'">
                {{ record.status }}
              </a-tag>
              <a-tag v-if="record.isCurrent" color="gold">当前</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button v-if="record.status === 'draft'" type="link" size="small" @click="handlePublishVersion(record)">发布</a-button>
                <a-button v-if="record.status === 'published' && !record.isCurrent" type="link" size="small" @click="handleDeprecateVersion(record)">废弃</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="deployments" tab="部署管理">
        <div style="margin-bottom: 16px;">
          <a-button type="primary" @click="showDeployModal">创建部署</a-button>
        </div>
        <a-table :columns="deploymentColumns" :data-source="deployments" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag>{{ record.status }}</a-tag>
            </template>
            <template v-if="column.key === 'progress'">
              {{ record.successCount || 0 }}/{{ record.totalDevices || 0 }}
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button v-if="record.status === 'failed'" type="link" size="small" @click="handleRetryDeploy(record)">重试</a-button>
                <a-button v-if="record.status === 'pending' || record.status === 'in_progress'" type="link" size="small" danger @click="handleCancelDeploy(record)">取消</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- Create Version Modal -->
    <a-modal v-model:open="versionModalVisible" title="创建版本" @ok="handleCreateVersion">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="版本号" required>
          <a-input v-model:value="versionForm.version" placeholder="v1.0.0" />
        </a-form-item>
        <a-form-item label="更新日志">
          <a-textarea v-model:value="versionForm.changeLog" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Deploy Modal -->
    <a-modal v-model:open="deployModalVisible" title="创建部署" @ok="handleDeploy">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="设备ID列表" required>
          <a-select v-model:value="deployForm.deviceIds" mode="tags" placeholder="输入设备ID" style="width: 100%" />
        </a-form-item>
        <a-form-item label="版本">
          <a-input v-model:value="deployForm.version" placeholder="留空使用当前版本" />
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
  getModel, publishModel, deprecateModel, listVersions, createVersion,
  publishVersion, deprecateVersion, createDeployment, listDeployments,
  retryDeployment, cancelDeployment,
} from '@/api/model';

const route = useRoute();
const modelId = route.params.id as string;

const model = ref<any>(null);
const activeTab = ref('versions');
const versions = ref<any[]>([]);
const deployments = ref<any[]>([]);
const versionModalVisible = ref(false);
const versionForm = ref({ version: '', changeLog: '' });
const deployModalVisible = ref(false);
const deployForm = ref({ deviceIds: [] as string[], version: '' });

const versionColumns = [
  { title: '版本', dataIndex: 'version', width: 120 },
  { title: '状态', key: 'status', width: 160 },
  { title: '更新日志', dataIndex: 'changeLog', ellipsis: true },
  { title: '发布时间', dataIndex: 'publishedAt', width: 170 },
  { title: '操作', key: 'action', width: 160 },
];

const deploymentColumns = [
  { title: '目标版本', dataIndex: 'targetVersion', width: 120 },
  { title: '状态', key: 'status', width: 120 },
  { title: '进度', key: 'progress', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 150 },
];

const loadModel = async () => { try { model.value = await getModel(modelId); } catch { message.error('加载模型失败'); } };
const loadVersions = async () => { try { versions.value = await listVersions(modelId); } catch { message.error('加载版本失败'); } };
const loadDeployments = async () => {
  try {
    const res = await listDeployments(modelId);
    deployments.value = res.items;
  } catch { message.error('加载部署失败'); }
};

const handlePublish = async () => { try { await publishModel(modelId); message.success('已发布'); loadModel(); } catch { message.error('发布失败'); } };
const handleDeprecate = async () => { try { await deprecateModel(modelId); message.success('已废弃'); loadModel(); } catch { message.error('操作失败'); } };

const showCreateVersionModal = () => { versionForm.value = { version: '', changeLog: '' }; versionModalVisible.value = true; };
const handleCreateVersion = async () => {
  try { await createVersion(modelId, versionForm.value); message.success('版本已创建'); versionModalVisible.value = false; loadVersions(); } catch { message.error('创建失败'); }
};
const handlePublishVersion = async (v: any) => { try { await publishVersion(modelId, v.id); message.success('版本已发布'); loadVersions(); loadModel(); } catch { message.error('发布失败'); } };
const handleDeprecateVersion = async (v: any) => { try { await deprecateVersion(modelId, v.id); message.success('版本已废弃'); loadVersions(); } catch { message.error('操作失败'); } };

const showDeployModal = () => { deployForm.value = { deviceIds: [], version: '' }; deployModalVisible.value = true; };
const handleDeploy = async () => {
  try { await createDeployment(modelId, deployForm.value); message.success('部署已创建'); deployModalVisible.value = false; loadDeployments(); } catch { message.error('创建失败'); }
};
const handleRetryDeploy = async (d: any) => { try { await retryDeployment(modelId, d.id); message.success('重试已启动'); loadDeployments(); } catch { message.error('重试失败'); } };
const handleCancelDeploy = async (d: any) => { try { await cancelDeployment(modelId, d.id); message.success('已取消'); loadDeployments(); } catch { message.error('取消失败'); } };

onMounted(() => { loadModel(); loadVersions(); loadDeployments(); });
</script>
