<template>
  <div class="model-list">
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>模型管理</span>
          <a-tag color="blue">{{ total }} 个模型</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="showCreateModal">
          <template #icon><PlusOutlined /></template>
          添加模型
        </a-button>
      </template>

      <div class="filter-bar">
        <a-input-search
          v-model:value="searchText"
          placeholder="搜索模型名称"
          style="width: 300px"
          @search="handleSearch"
          allow-clear
        />
      </div>

      <a-table
        :columns="columns"
        :data-source="models"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a @click="showDetailModal(record)">{{ record.name }}</a>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'active' ? 'green' : 'default'">
              {{ record.status === 'active' ? '活跃' : record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
              <a-popconfirm title="确定要删除此模型吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑模型弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑模型' : '添加模型'"
      :confirm-loading="modalLoading"
      @ok="handleModalOk"
      @cancel="modalVisible = false"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :rules="formRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-form-item label="模型名称" name="name">
          <a-input v-model:value="formState.name" placeholder="请输入模型名称" />
        </a-form-item>
        <a-form-item label="模型类型" name="type">
          <a-select v-model:value="formState.type" placeholder="选择类型">
            <a-select-option value="classification">分类</a-select-option>
            <a-select-option value="detection">检测</a-select-option>
            <a-select-option value="segmentation">分割</a-select-option>
            <a-select-option value="anomaly">异常检测</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="formState.description" :rows="3" placeholder="模型描述" />
        </a-form-item>
        <a-form-item label="框架" name="framework">
          <a-select v-model:value="formState.framework" placeholder="选择框架" allow-clear>
            <a-select-option value="pytorch">PyTorch</a-select-option>
            <a-select-option value="tensorflow">TensorFlow</a-select-option>
            <a-select-option value="onnx">ONNX</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailModalVisible"
      :title="detailModel?.name"
      :footer="null"
      width="700px"
    >
      <a-descriptions :column="2" bordered v-if="detailModel">
        <a-descriptions-item label="类型">{{ detailModel.type }}</a-descriptions-item>
        <a-descriptions-item label="框架">{{ detailModel.framework || '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="detailModel.status === 'active' ? 'green' : 'default'">
            {{ detailModel.status === 'active' ? '活跃' : detailModel.status }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">{{ detailModel.description || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import type { TableProps, FormInstance } from 'ant-design-vue';
import {
  getModelList,
  createModel,
  updateModel,
  deleteModel,
  type AiModel,
  type CreateModelDto,
} from '@/api/model';

const models = ref<AiModel[]>([]);
const total = ref(0);
const loading = ref(false);
const searchText = ref('');

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
});

const columns = [
  { title: '模型名称', key: 'name', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '框架', dataIndex: 'framework', key: 'framework', width: 120 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170,
    customRender: ({ text }: { text: string }) => new Date(text).toLocaleString('zh-CN'),
  },
  { title: '操作', key: 'action', width: 160 },
];

// Modal state
const modalVisible = ref(false);
const modalLoading = ref(false);
const isEdit = ref(false);
const editingId = ref<string | null>(null);
const formRef = ref<FormInstance>();

const formState = reactive<CreateModelDto & { framework?: string }>({
  name: '',
  type: '',
  description: '',
  framework: undefined,
});

const formRules = {
  name: [{ required: true, message: '请输入模型名称' }],
  type: [{ required: true, message: '请选择模型类型' }],
};

// Detail modal
const detailModalVisible = ref(false);
const detailModel = ref<AiModel | null>(null);

const loadModels = async () => {
  loading.value = true;
  try {
    const res = await getModelList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      search: searchText.value || undefined,
    });
    models.value = res.items;
    total.value = res.total;
    pagination.total = res.total;
  } catch {
    message.error('加载模型列表失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.current = 1;
  loadModels();
};

const handleTableChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current || 1;
  pagination.pageSize = pag.pageSize || 20;
  loadModels();
};

const showCreateModal = () => {
  isEdit.value = false;
  editingId.value = null;
  formState.name = '';
  formState.type = '';
  formState.description = '';
  formState.framework = undefined;
  modalVisible.value = true;
};

const showEditModal = (model: AiModel) => {
  isEdit.value = true;
  editingId.value = model.id;
  formState.name = model.name;
  formState.type = model.type;
  formState.description = model.description || '';
  formState.framework = model.framework || undefined;
  modalVisible.value = true;
};

const handleModalOk = async () => {
  try {
    await formRef.value?.validate();
    modalLoading.value = true;
    if (isEdit.value && editingId.value) {
      await updateModel(editingId.value, formState);
      message.success('模型更新成功');
    } else {
      await createModel(formState);
      message.success('模型创建成功');
    }
    modalVisible.value = false;
    loadModels();
  } catch {
    // validation or API error
  } finally {
    modalLoading.value = false;
  }
};

const handleDelete = async (id: string) => {
  try {
    await deleteModel(id);
    message.success('模型已删除');
    loadModels();
  } catch {
    message.error('删除失败');
  }
};

const showDetailModal = (model: AiModel) => {
  detailModel.value = model;
  detailModalVisible.value = true;
};

onMounted(() => {
  loadModels();
});
</script>

<style scoped>
.model-list {
  padding: 24px;
}

.filter-bar {
  margin-bottom: 16px;
}
</style>
