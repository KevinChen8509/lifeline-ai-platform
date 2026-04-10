<template>
  <div class="user-management">
    <a-card :bordered="false">
      <template #title>
        <div class="card-header">
          <span>用户管理</span>
          <a-button type="primary" @click="showCreateModal">
            <template #icon><PlusOutlined /></template>
            新增用户
          </a-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <div class="search-form">
        <a-form layout="inline">
          <a-form-item label="状态">
            <a-select
              v-model:value="searchParams.status"
              style="width: 120px"
              allowClear
              placeholder="全部"
            >
              <a-select-option value="PENDING">待激活</a-select-option>
              <a-select-option value="ACTIVE">已激活</a-select-option>
              <a-select-option value="DISABLED">已禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="角色">
            <a-select
              v-model:value="searchParams.roleId"
              style="width: 140px"
              allowClear
              placeholder="全部角色"
            >
              <a-select-option v-for="role in roles" :key="role.id" :value="role.id">
                {{ role.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="搜索">
            <a-input-search
              v-model:value="searchParams.search"
              placeholder="用户名/姓名/邮箱"
              style="width: 200px"
              @search="handleSearch"
            />
          </a-form-item>
        </a-form>
      </div>

      <!-- 用户列表 -->
      <a-table
        :columns="columns"
        :data-source="users"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        rowKey="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record)">
                编辑
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="showRoleModal(record)"
              >
                分配角色
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="handleActivate(record)"
                v-if="record.status === 'PENDING'"
              >
                激活
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="handleDisable(record)"
                v-if="record.status === 'ACTIVE'"
              >
                禁用
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="handleEnable(record)"
                v-if="record.status === 'DISABLED'"
              >
                启用
              </a-button>
              <a-button
                type="link"
                size="small"
                danger
                @click="handleDelete(record)"
              >
                删除
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑用户弹窗 -->
    <UserFormModal
      v-model:visible="formModalVisible"
      :user="currentUser"
      @success="handleFormSuccess"
    />

    <!-- 分配角色弹窗 -->
    <RoleAssignModal
      v-model:visible="roleModalVisible"
      :user="currentUser"
      @success="handleRoleSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { request } from '@/api/request';
import { getRoles } from '@/api/role';
import UserFormModal from './components/UserFormModal.vue';
import RoleAssignModal from './components/RoleAssignModal.vue';

interface Role {
  id: string;
  name: string;
  code: string;
}

interface User {
  id: string;
  username: string;
  name: string;
  email?: string;
  phone?: string;
  status: string;
  roleId?: string;
  role?: { id: string; name: string; code: string };
  createdAt: string;
}

const loading = ref(false);
const users = ref<User[]>([]);
const roles = ref<Role[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const formModalVisible = ref(false);
const roleModalVisible = ref(false);
const currentUser = ref<User | null>(null);

const searchParams = reactive({
  status: undefined as string | undefined,
  roleId: undefined as string | undefined,
  search: '',
});

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '手机号', dataIndex: 'phone', key: 'phone' },
  { title: '角色', dataIndex: ['role', 'name'], key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action', width: 280 },
];

const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}));

const getStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    PENDING: 'orange',
    ACTIVE: 'green',
    DISABLED: 'red',
  };
  return colors[status] || 'default';
};

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    PENDING: '待激活',
    ACTIVE: '已激活',
    DISABLED: '已禁用',
  };
  return texts[status] || status;
};

const fetchUsers = async () => {
  loading.value = true;
  try {
    const result = await request.get<{
      items: User[];
      total: number;
      page: number;
      pageSize: number;
    }>('/users', {
      params: {
        page: currentPage.value,
        pageSize: pageSize.value,
        ...searchParams,
      },
    });
    users.value = result.items;
    total.value = result.total;
  } catch (error) {
    message.error('获取用户列表失败');
  } finally {
    loading.value = false;
  }
};

const fetchRoles = async () => {
  try {
    roles.value = await getRoles();
  } catch (error) {
    // 角色列表获取失败不影响主流程
    console.error('获取角色列表失败', error);
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchUsers();
};

const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
  currentPage.value = pag.current || 1;
  pageSize.value = pag.pageSize || 20;
  fetchUsers();
};

const showCreateModal = () => {
  currentUser.value = null;
  formModalVisible.value = true;
};

const showEditModal = (user: User) => {
  currentUser.value = user;
  formModalVisible.value = true;
};

const showRoleModal = (user: User) => {
  currentUser.value = user;
  roleModalVisible.value = true;
};

const handleFormSuccess = () => {
  formModalVisible.value = false;
  fetchUsers();
};

const handleRoleSuccess = () => {
  roleModalVisible.value = false;
  fetchUsers();
};

const handleActivate = async (user: User) => {
  Modal.confirm({
    title: '确认激活',
    content: `确定要激活用户 "${user.username}" 吗？`,
    onOk: async () => {
      try {
        await request.put(`/users/${user.id}/status`, { status: 'ACTIVE' });
        message.success('用户已激活');
        fetchUsers();
      } catch (error) {
        message.error('激活失败');
      }
    },
  });
};

const handleDisable = async (user: User) => {
  Modal.confirm({
    title: '确认禁用',
    content: `确定要禁用用户 "${user.username}" 吗？禁用后该用户将无法登录系统。`,
    okType: 'danger',
    onOk: async () => {
      try {
        await request.put(`/users/${user.id}/status`, { status: 'DISABLED' });
        message.success('用户已禁用');
        fetchUsers();
      } catch (error) {
        message.error('禁用失败');
      }
    },
  });
};

const handleEnable = async (user: User) => {
  Modal.confirm({
    title: '确认启用',
    content: `确定要启用用户 "${user.username}" 吗？`,
    onOk: async () => {
      try {
        await request.put(`/users/${user.id}/status`, { status: 'ACTIVE' });
        message.success('用户已启用');
        fetchUsers();
      } catch (error) {
        message.error('启用失败');
      }
    },
  });
};

const handleDelete = (user: User) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除用户 "${user.username}" 吗？此操作不可恢复。`,
    okType: 'danger',
    onOk: async () => {
      try {
        await request.delete(`/users/${user.id}`);
        message.success('用户已删除');
        fetchUsers();
      } catch (error) {
        message.error('删除失败');
      }
    },
  });
};

onMounted(() => {
  fetchUsers();
  fetchRoles();
});
</script>

<style scoped>
.user-management {
  padding: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 16px;
}
</style>
