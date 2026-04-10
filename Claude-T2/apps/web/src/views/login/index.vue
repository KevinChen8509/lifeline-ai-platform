<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <img src="/favicon.svg" alt="Logo" class="logo" />
        <h1>生命线AI感知云平台</h1>
        <p>Cloud-Edge-Device IoT Platform</p>
      </div>

      <a-form
        :model="formState"
        :rules="rules"
        @finish="handleLogin"
        layout="vertical"
        class="login-form"
      >
        <a-form-item name="username" label="用户名">
          <a-input
            v-model:value="formState.username"
            placeholder="请输入用户名"
            size="large"
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="password" label="密码">
          <a-input-password
            v-model:value="formState.password"
            placeholder="请输入密码"
            size="large"
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <a-checkbox v-model:checked="formState.remember">记住我</a-checkbox>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            :loading="loading"
            block
            size="large"
          >
            登录
          </a-button>
        </a-form-item>
      </a-form>

      <div class="login-footer">
        <p>© 2026 生命线AI感知云平台 - 城市生命线基础设施智能监测</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { request } from '@/api/request';

const router = useRouter();
const userStore = useUserStore();
const permissionStore = usePermissionStore();

const loading = ref(false);

const formState = reactive({
  username: '',
  password: '',
  remember: false,
});

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为3-50个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6个字符', trigger: 'blur' },
  ],
};

async function handleLogin() {
  loading.value = true;

  try {
    const result = await request.post<{
      accessToken: string;
      refreshToken: string;
      user: {
        id: string;
        username: string;
        name: string;
        email?: string;
        phone?: string;
        status: string;
        roleId?: string;
        role?: { id: string; name: string; code: string };
      };
      permissions: Array<{ action: string; subject: string }>;
    }>('/auth/login', {
      username: formState.username,
      password: formState.password,
    });

    // 存储 Token 和用户信息
    userStore.setTokens(result.accessToken, result.refreshToken);
    userStore.setUser(result.user);

    // 存储权限
    if (result.permissions) {
      permissionStore.setPermissions(result.permissions);
    }

    message.success('登录成功');
    router.push('/dashboard');
  } catch (error: any) {
    // 错误已由 request 拦截器处理
    // 这里只需要保持表单数据
    console.error('登录失败:', error);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #001529 0%, #0050b3 100%);
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header .logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
}

.login-header h1 {
  margin: 0;
  font-size: 24px;
  color: #001529;
}

.login-header p {
  margin: 8px 0 0;
  color: #666;
  font-size: 14px;
}

.login-form {
  margin-top: 24px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  color: #999;
  font-size: 12px;
}
</style>
