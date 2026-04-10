<template>
  <a-modal
    :open="visible"
    :title="`分配角色 - ${user?.username || ''}`"
    :confirmLoading="loading"
    @ok="handleSubmit"
    @cancel="handleCancel"
    width="480px"
  >
    <a-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      layout="vertical"
    >
      <a-form-item label="当前角色">
        <a-tag color="blue">{{ user?.role?.name || '无角色' }}</a-tag>
      </a-form-item>

      <a-form-item name="roleId" label="新角色">
        <a-select
          v-model:value="formState.roleId"
          placeholder="请选择角色"
          :loading="rolesLoading"
        >
          <a-select-option
            v-for="role in roles"
            :key="role.id"
            :value="role.id"
          >
            <div class="role-option">
              <span>{{ role.name }}</span>
              <span class="role-code">{{ role.code }}</span>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-alert
        v-if="user?.id === currentUserId"
        message="无法修改自己的角色"
        description="如需修改自己的角色，请联系其他管理员。"
        type="warning"
        show-icon
        style="margin-top: 16px"
      />
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import { message } from 'ant-design-vue';
import type { FormInstance } from 'ant-design-vue';
import { request } from '@/api/request';
import { useUserStore } from '@/stores/user';

interface Props {
  visible: boolean;
  user: {
    id: string;
    username: string;
    roleId?: string;
    role?: { id: string; name: string; code: string };
  } | null;
}

interface Emits {
  (e: 'update:visible', visible: boolean): void;
  (e: 'success'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const userStore = useUserStore();
const currentUserId = computed(() => userStore.user?.id);

const formRef = ref<FormInstance>();
const loading = ref(false);
const rolesLoading = ref(false);
const roles = ref<Array<{ id: string; name: string; code: string; description?: string }>>([]);

const formState = reactive({
  roleId: '',
});

const rules = {
  roleId: [{ required: true, message: '请选择角色' }],
};

// 监听 user 变化，初始化表单
watch(
  () => props.user,
  (newUser) => {
    if (newUser) {
      formState.roleId = newUser.roleId || '';
    }
  },
  { immediate: true }
);

// 监听 visible 变化，加载角色列表
watch(
  () => props.visible,
  async (newVisible) => {
    if (newVisible) {
      await fetchRoles();
    }
  }
);

const fetchRoles = async () => {
  rolesLoading.value = true;
  try {
    const result = await request.get<Array<{ id: string; name: string; code: string }>>('/roles');
    roles.value = result;
  } catch (error) {
    message.error('获取角色列表失败');
  } finally {
    rolesLoading.value = false;
  }
};

const handleSubmit = async () => {
  // 检查是否修改自己的角色
  if (props.user?.id === currentUserId.value) {
    message.warning('无法修改自己的角色');
    return;
  }

  try {
    await formRef.value?.validate();
  } catch {
    return;
  }

  // 检查是否选择了相同的角色
  if (formState.roleId === props.user?.roleId) {
    message.info('角色未变更');
    emit('update:visible', false);
    return;
  }

  loading.value = true;
  try {
    await request.put(`/users/${props.user?.id}/role`, {
      roleId: formState.roleId,
    });
    message.success('角色分配成功');
    emit('success');
    emit('update:visible', false);
  } catch (error: any) {
    const errorMsg = error?.response?.data?.message || '角色分配失败';
    message.error(errorMsg);
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  emit('update:visible', false);
};
</script>

<style scoped>
.role-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-code {
  color: #999;
  font-size: 12px;
}
</style>
