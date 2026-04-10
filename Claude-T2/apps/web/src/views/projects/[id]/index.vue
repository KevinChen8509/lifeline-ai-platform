<template>
  <div class="project-detail">
    <a-page-header
      :title="project?.name || '加载中...'"
      :sub-title="project?.code"
      @back="goBack"
    >
      <template #extra>
        <a-space>
          <a-button @click="showEditModal">编辑</a-button>
          <a-popconfirm
            v-if="project?.status === 'active'"
            title="确定要归档此项目吗？"
            @confirm="handleArchive"
          >
            <a-button danger>归档</a-button>
          </a-popconfirm>
          <a-popconfirm
            v-if="project?.status === 'archived'"
            title="确定要恢复此项目吗？"
            @confirm="handleRestore"
          >
            <a-button type="primary">恢复</a-button>
          </a-popconfirm>
        </a-space>
      </template>
      <template #tags>
        <a-tag :color="project?.status === 'active' ? 'green' : 'default'">
          {{ project?.status === 'active' ? '活跃' : '归档' }}
        </a-tag>
      </template>
      <template #footer>
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="overview" tab="概览" />
          <a-tab-pane key="devices" tab="设备" />
          <a-tab-pane key="members" tab="成员" />
          <a-tab-pane key="settings" tab="配置" />
        </a-tabs>
      </template>
    </a-page-header>

    <div class="content">
      <a-spin :spinning="loading">
        <!-- 概览 Tab -->
        <div v-if="activeTab === 'overview'" class="overview-tab">
          <!-- 设备统计卡片 -->
          <a-row :gutter="16">
            <a-col :span="6">
              <a-card>
                <a-statistic
                  title="总设备数"
                  :value="overview?.stats?.totalDevices || 0"
                >
                  <template #prefix>
                    <DesktopOutlined />
                  </template>
                </a-statistic>
              </a-card>
            </a-col>
            <a-col :span="6">
              <a-card>
                <a-statistic
                  title="在线设备"
                  :value="overview?.stats?.onlineDevices || 0"
                  :value-style="{ color: '#3f8600' }"
                >
                  <template #prefix>
                    <CheckCircleOutlined />
                  </template>
                </a-statistic>
              </a-card>
            </a-col>
            <a-col :span="6">
              <a-card>
                <a-statistic
                  title="离线设备"
                  :value="overview?.stats?.offlineDevices || 0"
                  :value-style="{ color: '#999' }"
                >
                  <template #prefix>
                    <CloseCircleOutlined />
                  </template>
                </a-statistic>
              </a-card>
            </a-col>
            <a-col :span="6">
              <a-card>
                <a-statistic
                  title="告警设备"
                  :value="overview?.stats?.alertDevices || 0"
                  :value-style="{ color: '#cf1322' }"
                >
                  <template #prefix>
                    <WarningOutlined />
                  </template>
                </a-statistic>
              </a-card>
            </a-col>
          </a-row>

          <!-- 最近活动 -->
          <a-card title="最近活动" style="margin-top: 16px">
            <a-empty v-if="!overview?.recentActivities?.length" description="暂无活动记录" />
            <a-timeline v-else>
              <a-timeline-item
                v-for="activity in overview?.recentActivities"
                :key="activity.id"
                :color="getActivityColor(activity.type)"
              >
                <p>{{ activity.description }}</p>
                <p class="activity-time">{{ formatTime(activity.timestamp) }}</p>
              </a-timeline-item>
            </a-timeline>
          </a-card>
        </div>

        <!-- 设备 Tab (占位) -->
        <div v-else-if="activeTab === 'devices'" class="devices-tab">
          <a-empty description="设备管理功能即将上线" />
        </div>

        <!-- 成员 Tab -->
        <div v-else-if="activeTab === 'members'" class="members-tab">
          <a-card>
            <template #title>
              <a-space>
                <span>项目成员</span>
                <a-tag color="blue">{{ members.length }} 人</a-tag>
              </a-space>
            </template>
            <a-table
              :columns="memberColumns"
              :data-source="members"
              :loading="membersLoading"
              row-key="id"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'role'">
                  <a-tag :color="record.role === 'admin' ? 'gold' : 'blue'">
                    {{ record.role === 'admin' ? '管理员' : '成员' }}
                  </a-tag>
                </template>
              </template>
            </a-table>
          </a-card>
        </div>

        <!-- 配置 Tab -->
        <div v-else-if="activeTab === 'settings'" class="settings-tab">
          <a-card title="项目配置">
            <a-descriptions :column="1" bordered>
              <a-descriptions-item label="项目名称">{{ project?.name }}</a-descriptions-item>
              <a-descriptions-item label="项目编码">{{ project?.code }}</a-descriptions-item>
              <a-descriptions-item label="项目描述">{{ project?.description || '-' }}</a-descriptions-item>
              <a-descriptions-item label="创建时间">{{ formatTime(project?.createdAt) }}</a-descriptions-item>
              <a-descriptions-item label="更新时间">{{ formatTime(project?.updatedAt) }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </div>
      </a-spin>
    </div>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑项目"
      :confirm-loading="editLoading"
      @ok="handleEditOk"
    >
      <a-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-form-item label="项目名称" name="name">
          <a-input v-model:value="editForm.name" />
        </a-form-item>
        <a-form-item label="项目描述" name="description">
          <a-textarea v-model:value="editForm.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  DesktopOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue';
import type { FormInstance } from 'ant-design-vue';
import {
  getProject,
  getProjectOverview,
  updateProject,
  archiveProject,
  restoreProject,
  type Project,
} from '@/api/project';

const route = useRoute();
const router = useRouter();

const projectId = route.params.id as string;
const activeTab = ref('overview');
const loading = ref(true);
const project = ref<Project | null>(null);
const overview = ref<any>(null);
const members = ref<any[]>([]);
const membersLoading = ref(false);

// 编辑弹窗
const editModalVisible = ref(false);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();
const editForm = reactive({
  name: '',
  description: '',
});
const editRules = {
  name: [
    { required: true, message: '请输入项目名称' },
    { max: 100, message: '项目名称不能超过100个字符' },
  ],
};

// 成员表格列
const memberColumns = [
  { title: '用户名', dataIndex: 'user', key: 'user' },
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '角色', key: 'role' },
  { title: '加入时间', dataIndex: 'joinedAt', key: 'joinedAt' },
];

// 加载项目详情
const loadProject = async () => {
  loading.value = true;
  try {
    project.value = await getProject(projectId);
    // 提取成员信息
    members.value = project.value.members || [];
  } catch (error) {
    message.error('加载项目失败');
    router.push('/projects');
  } finally {
    loading.value = false;
  }
};

// 加载项目概览
const loadOverview = async () => {
  try {
    overview.value = await getProjectOverview(projectId);
  } catch (error) {
    console.error('加载概览失败', error);
  }
};

// 返回上一页
const goBack = () => {
  router.push('/projects');
};

// 显示编辑弹窗
const showEditModal = () => {
  editForm.name = project.value?.name || '';
  editForm.description = project.value?.description || '';
  editModalVisible.value = true;
};

// 确认编辑
const handleEditOk = async () => {
  try {
    await editFormRef.value?.validate();
    editLoading.value = true;
    await updateProject(projectId, editForm);
    message.success('更新成功');
    editModalVisible.value = false;
    loadProject();
  } catch (error) {
    // 验证失败或API错误
  } finally {
    editLoading.value = false;
  }
};

// 归档项目
const handleArchive = async () => {
  try {
    await archiveProject(projectId);
    message.success('项目已归档');
    router.push('/projects');
  } catch (error) {
    message.error('归档失败');
  }
};

// 恢复项目
const handleRestore = async () => {
  try {
    await restoreProject(projectId);
    message.success('项目已恢复');
    loadProject();
  } catch (error) {
    message.error('恢复失败');
  }
};

// 获取活动颜色
const getActivityColor = (type: string): string => {
  const colors: Record<string, string> = {
    device_online: 'green',
    device_offline: 'gray',
    device_alert: 'red',
    device_created: 'blue',
  };
  return colors[type] || 'blue';
};

// 格式化时间
const formatTime = (time?: string | Date): string => {
  if (!time) return '-';
  return new Date(time).toLocaleString('zh-CN');
};

onMounted(() => {
  loadProject();
  loadOverview();
});
</script>

<style scoped>
.project-detail {
  padding: 24px;
}

.content {
  margin-top: 16px;
}

.activity-time {
  color: #999;
  font-size: 12px;
  margin: 0;
}
</style>
