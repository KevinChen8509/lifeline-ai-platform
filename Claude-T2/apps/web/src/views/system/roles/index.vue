<template>
  <div class="role-management">
    <a-card :bordered="false">
      <template #title>
        <div class="card-header">
          <span>角色管理</span>
          <a-button type="primary" @click="showCreateModal">
            <template #icon><PlusOutlined /></template>
            新增角色
          </a-button>
        </div>
      </template>

      <a-table
        :columns="columns"
        :data-source="roles"
        :loading="loading"
        row-key="id"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'permissions'">
            <a-tag v-for="p in (record.permissions || []).slice(0, 3)" :key="p.id" color="blue">
              {{ p.name }}
            </a-tag>
            <span v-if="(record.permissions || []).length > 3" class="more-tag">
              +{{ record.permissions.length - 3 }}
            </span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
              <a-popconfirm title="确定要删除此角色吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑角色弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑角色' : '新增角色'"
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
        <a-form-item label="角色名称" name="name">
          <a-input v-model:value="formState.name" placeholder="请输入角色名称" />
        </a-form-item>
        <a-form-item label="角色编码" name="code">
          <a-input
            v-model:value="formState.code"
            placeholder="如: admin, operator"
            :disabled="isEdit"
          />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="formState.description" :rows="3" placeholder="角色描述" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import type { FormInstance } from 'ant-design-vue';
import { request } from '@/api/request';

interface Role {
  id: string;
  name: string;
  code: string;
  description: string | null;
  permissions: Array<{ id: string; name: string; code: string }>;
  createdAt: string;
}

const loading = ref(false);
const roles = ref<Role[]>([]);

const columns = [
  { title: '角色名称', dataIndex: 'name', key: 'name' },
  { title: '角色编码', dataIndex: 'code', key: 'code' },
  { title: '权限', key: 'permissions' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 160 },
];

// Modal state
const modalVisible = ref(false);
const modalLoading = ref(false);
const isEdit = ref(false);
const editingId = ref<string | null>(null);
const formRef = ref<FormInstance>();

const formState = reactive({
  name: '',
  code: '',
  description: '',
});

const formRules = {
  name: [{ required: true, message: '请输入角色名称' }],
  code: [
    { required: true, message: '请输入角色编码' },
    { pattern: /^[a-z_]+$/, message: '只能包含小写字母和下划线' },
  ],
};

const loadRoles = async () => {
  loading.value = true;
  try {
    roles.value = await request.get<Role[]>('/roles');
  } catch {
    message.error('加载角色列表失败');
  } finally {
    loading.value = false;
  }
};

const showCreateModal = () => {
  isEdit.value = false;
  editingId.value = null;
  formState.name = '';
  formState.code = '';
  formState.description = '';
  modalVisible.value = true;
};

const showEditModal = (role: Role) => {
  isEdit.value = true;
  editingId.value = role.id;
  formState.name = role.name;
  formState.code = role.code;
  formState.description = role.description || '';
  modalVisible.value = true;
};

const handleModalOk = async () => {
  try {
    await formRef.value?.validate();
    modalLoading.value = true;
    if (isEdit.value && editingId.value) {
      await request.put(`/roles/${editingId.value}`, formState);
      message.success('角色更新成功');
    } else {
      await request.post('/roles', formState);
      message.success('角色创建成功');
    }
    modalVisible.value = false;
    loadRoles();
  } catch {
    // validation or API error
  } finally {
    modalLoading.value = false;
  }
};

const handleDelete = async (id: string) => {
  try {
    await request.delete(`/roles/${id}`);
    message.success('角色已删除');
    loadRoles();
  } catch {
    message.error('删除失败');
  }
};

onMounted(() => {
  loadRoles();
});
</script>

<style scoped>
.role-management {
  padding: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.more-tag {
  color: #999;
  font-size: 12px;
  margin-left: 4px;
}
</style>
