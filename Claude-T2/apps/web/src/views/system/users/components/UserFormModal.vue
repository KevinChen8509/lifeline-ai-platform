<template>
  <a-modal
    :open="visible"
    :title="isEdit ? '编辑用户' : '新增用户'"
    :confirmLoading="loading"
    @ok="handleSubmit"
    @cancel="handleCancel"
    width="600px"
  >
    <a-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      :labelCol="{ span: 6 }"
      :wrapperCol="{ span: 16 }"
    >
      <a-form-item label="用户名" name="username">
        <a-input
          v-model:value="formState.username"
          placeholder="请输入用户名"
          :disabled="isEdit"
          @blur="handleUsernameBlur"
        >
          <template #suffix>
            <span v-if="usernameChecking" style="color: #999">
              <LoadingOutlined /> 检查中...
            </span>
            <span v-else-if="usernameAvailable === true" style="color: #52c41a">
              <CheckCircleOutlined /> 可用
            </span>
            <span v-else-if="usernameAvailable === false" style="color: #ff4d4f">
              <CloseCircleOutlined /> 已存在
            </span>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item v-if="!isEdit" label="密码" name="password">
        <a-input-password
          v-model:value="formState.password"
          placeholder="请输入密码（至少6位，包含字母和数字）"
        />
      </a-form-item>

      <a-form-item label="姓名" name="name">
        <a-input v-model:value="formState.name" placeholder="请输入姓名" />
      </a-form-item>

      <a-form-item label="邮箱" name="email">
        <a-input v-model:value="formState.email" placeholder="请输入邮箱" />
      </a-form-item>

      <a-form-item label="手机号" name="phone">
        <a-input v-model:value="formState.phone" placeholder="请输入手机号" />
      </a-form-item>

      <a-form-item label="角色" name="roleId">
        <a-select
          v-model:value="formState.roleId"
          placeholder="请选择角色"
          allowClear
        >
          <a-select-option v-for="role in roles" :key="role.id" :value="role.id">
            {{ role.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import { message } from 'ant-design-vue';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue';
import type { FormInstance } from 'ant-design-vue';
import { request } from '@/api/request';

interface Props {
  visible: boolean;
  user: User | null;
}

interface User {
  id: string;
  username: string;
  name: string;
  email?: string;
  phone?: string;
  roleId?: string;
}

interface Role {
  id: string;
  name: string;
  code: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void;
  (e: 'success'): void;
}>();

const formRef = ref<FormInstance>();
const loading = ref(false);
const roles = ref<Role[]>([]);
const usernameChecking = ref(false);
const usernameAvailable = ref<boolean | null>(null);

const isEdit = computed(() => !!props.user);

const formState = reactive({
  username: '',
  password: '',
  name: '',
  email: '',
  phone: '',
  roleId: undefined as string | undefined,
});

const rules = {
  username: [
    { required: true, message: '请输入用户名' },
    { min: 3, max: 50, message: '用户名长度为3-50个字符' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, max: 100, message: '密码长度为6-100个字符' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码必须包含字母和数字' },
  ],
  name: [
    { required: true, message: '请输入姓名' },
    { max: 50, message: '姓名长度不能超过50个字符' },
  ],
  email: [
    { type: 'email', message: '请输入有效的邮箱地址' },
    { max: 100, message: '邮箱长度不能超过100个字符' },
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' },
  ],
};

// 监听 user 变化，填充表单
watch(
  () => props.user,
  (user) => {
    if (user) {
      formState.username = user.username;
      formState.name = user.name;
      formState.email = user.email || '';
      formState.phone = user.phone || '';
      formState.roleId = user.roleId;
      usernameAvailable.value = true; // 编辑时用户名已存在
    } else {
      resetForm();
    }
  },
  { immediate: true }
);

// 监听 visible 变化，加载角色列表
watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      await fetchRoles();
    }
  }
);

const fetchRoles = async () => {
  try {
    const result = await request.get<{ items: Role[] }>('/roles');
    roles.value = result.items || [];
  } catch (error) {
    console.error('获取角色列表失败', error);
  }
};

const handleUsernameBlur = async () => {
  if (!formState.username || formState.username.length < 3) {
    usernameAvailable.value = null;
    return;
  }

  // 编辑模式不检查
  if (isEdit.value) {
    usernameAvailable.value = true;
    return;
  }

  usernameChecking.value = true;
  try {
    const result = await request.get<{ available: boolean }>('/users/check-username', {
      params: { username: formState.username },
    });
    usernameAvailable.value = result.available;
  } catch (error) {
    usernameAvailable.value = null;
  } finally {
    usernameChecking.value = false;
  }
};

const handleSubmit = async () => {
  try {
    await formRef.value?.validate();

    // 新建模式下检查用户名
    if (!isEdit.value && usernameAvailable.value === false) {
      message.error('用户名已存在，请更换');
      return;
    }

    loading.value = true;

    if (isEdit.value) {
      await request.put(`/users/${props.user!.id}`, {
        name: formState.name,
        email: formState.email || undefined,
        phone: formState.phone || undefined,
        roleId: formState.roleId,
      });
      message.success('用户更新成功');
    } else {
      await request.post('/users', {
        username: formState.username,
        password: formState.password,
        name: formState.name,
        email: formState.email || undefined,
        phone: formState.phone || undefined,
        roleId: formState.roleId,
      });
      message.success('用户创建成功');
    }

    emit('success');
  } catch (error: any) {
    if (error.response?.status === 409) {
      message.error('用户名已存在');
      usernameAvailable.value = false;
    } else if (error.response?.data?.message) {
      message.error(error.response.data.message);
    } else if (error.errorFields) {
      // 表单验证错误，已自动处理
    } else {
      message.error(isEdit.value ? '更新用户失败' : '创建用户失败');
    }
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  emit('update:visible', false);
};

const resetForm = () => {
  formState.username = '';
  formState.password = '';
  formState.name = '';
  formState.email = '';
  formState.phone = '';
  formState.roleId = undefined;
  usernameAvailable.value = null;
  formRef.value?.resetFields();
};
</script>
