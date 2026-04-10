<template>
  <div class="project-list">
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>项目列表</span>
          <a-tag color="blue">{{ total }} 个项目</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><PlusOutlined /></template>
          新建项目
        </a-button>
      </template>

      <!-- 搜索和筛选 -->
      <div class="filter-bar">
        <a-input-search
          v-model:value="searchText"
          placeholder="搜索项目名称或编码"
          style="width: 300px"
          @search="handleSearch"
          allow-clear
        />
        <a-select
          v-model:value="statusFilter"
          style="width: 150px; margin-left: 16px"
          placeholder="状态筛选"
          allow-clear
          @change="handleStatusChange"
        >
          <a-select-option value="active">活跃</a-select-option>
          <a-select-option value="archived">归档</a-select-option>
        </a-select>
      </div>

      <!-- 项目表格 -->
      <a-table
        :columns="columns"
        :data-source="projects"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a @click="goToProject(record.id)">{{ record.name }}</a>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'active' ? 'green' : 'default'">
              {{ record.status === 'active' ? '活跃' : '归档' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="goToProject(record.id)">
                查看
              </a-button>
              <a-button type="link" size="small" @click="showEditModal(record)">
                编辑
              </a-button>
              <a-popconfirm
                v-if="record.status === 'active'"
                title="确定要归档此项目吗？"
                @confirm="handleArchive(record.id)"
              >
                <a-button type="link" size="small" danger>归档</a-button>
              </a-popconfirm>
              <a-popconfirm
                v-if="record.status === 'archived'"
                title="确定要恢复此项目吗？"
                @confirm="handleRestore(record.id)"
              >
                <a-button type="link" size="small">恢复</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑项目弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑项目' : '新建项目'"
      :confirm-loading="modalLoading"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :rules="formRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-form-item label="项目名称" name="name">
          <a-input v-model:value="formState.name" placeholder="请输入项目名称" />
        </a-form-item>
        <a-form-item label="项目编码" name="code">
          <a-input
            v-model:value="formState.code"
            placeholder="大写字母开头，3-20字符"
            :disabled="isEdit"
          />
        </a-form-item>
        <a-form-item label="项目描述" name="description">
          <a-textarea
            v-model:value="formState.description"
            placeholder="请输入项目描述"
            :rows="3"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import type { TableProps, FormInstance } from 'ant-design-vue';
import {
  getProjectList,
  createProject,
  updateProject,
  archiveProject,
  restoreProject,
  type Project,
  type CreateProjectDto,
} from '@/api/project';

const router = useRouter();

// 列表数据
const projects = ref<Project[]>([]);
const total = ref(0);
const loading = ref(false);
const searchText = ref('');
const statusFilter = ref<string | undefined>(undefined);

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (t: number) => `共 ${t} 条`,
});

// 表格列定义
const columns = [
  {
    title: '项目名称',
    key: 'name',
    dataIndex: 'name',
    sorter: true,
  },
  {
    title: '项目编码',
    dataIndex: 'code',
    key: 'code',
  },
  {
    title: '描述',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true,
  },
  {
    title: '状态',
    key: 'status',
    dataIndex: 'status',
    filters: [
      { text: '活跃', value: 'active' },
      { text: '归档', value: 'archived' },
    ],
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    customRender: ({ text }: { text: string }) => {
      return new Date(text).toLocaleString('zh-CN');
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 200,
  },
];

// 弹窗相关
const modalVisible = ref(false);
const modalLoading = ref(false);
const isEdit = ref(false);
const editingProjectId = ref<string | null>(null);
const formRef = ref<FormInstance>();

const formState = reactive<CreateProjectDto>({
  name: '',
  code: '',
  description: '',
});

const formRules = {
  name: [
    { required: true, message: '请输入项目名称' },
    { max: 100, message: '项目名称不能超过100个字符' },
  ],
  code: [
    { required: true, message: '请输入项目编码' },
    { min: 3, max: 20, message: '项目编码长度为3-20个字符' },
    { pattern: /^[A-Z][A-Z0-9]{2,19}$/, message: '项目编码必须以大写字母开头，只能包含大写字母和数字' },
  ],
};

// 加载项目列表
const loadProjects = async () => {
  loading.value = true;
  try {
    const response = await getProjectList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      search: searchText.value || undefined,
      status: statusFilter.value as 'active' | 'archived' | undefined,
    });
    projects.value = response.items;
    total.value = response.total;
    pagination.total = response.total;
  } catch (error) {
    message.error('加载项目列表失败');
  } finally {
    loading.value = false;
  }
};

// 搜索处理
const handleSearch = () => {
  pagination.current = 1;
  loadProjects();
};

// 状态筛选处理
const handleStatusChange = () => {
  pagination.current = 1;
  loadProjects();
};

// 表格变化处理
const handleTableChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current || 1;
  pagination.pageSize = pag.pageSize || 20;
  loadProjects();
};

// 跳转到项目详情
const goToProject = (id: string) => {
  router.push(`/projects/${id}`);
};

// 显示创建弹窗
const showCreateModal = () => {
  isEdit.value = false;
  editingProjectId.value = null;
  formState.name = '';
  formState.code = '';
  formState.description = '';
  modalVisible.value = true;
};

// 显示编辑弹窗
const showEditModal = (project: Project) => {
  isEdit.value = true;
  editingProjectId.value = project.id;
  formState.name = project.name;
  formState.code = project.code;
  formState.description = project.description || '';
  modalVisible.value = true;
};

// 弹窗确认
const handleModalOk = async () => {
  try {
    await formRef.value?.validate();
    modalLoading.value = true;

    if (isEdit.value && editingProjectId.value) {
      await updateProject(editingProjectId.value, formState);
      message.success('项目更新成功');
    } else {
      await createProject(formState);
      message.success('项目创建成功');
    }

    modalVisible.value = false;
    loadProjects();
  } catch (error) {
    // 表单验证失败或API错误
  } finally {
    modalLoading.value = false;
  }
};

// 弹窗取消
const handleModalCancel = () => {
  modalVisible.value = false;
  formRef.value?.resetFields();
};

// 归档项目
const handleArchive = async (id: string) => {
  try {
    await archiveProject(id);
    message.success('项目已归档');
    loadProjects();
  } catch (error) {
    message.error('归档失败');
  }
};

// 恢复项目
const handleRestore = async (id: string) => {
  try {
    await restoreProject(id);
    message.success('项目已恢复');
    loadProjects();
  } catch (error) {
    message.error('恢复失败');
  }
};

onMounted(() => {
  loadProjects();
});
</script>

<style scoped>
.project-list {
  padding: 24px;
}

.filter-bar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
</style>
