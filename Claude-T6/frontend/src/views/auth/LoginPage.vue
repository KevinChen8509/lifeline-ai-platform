<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">IoT 数据订阅平台</h2>
      </template>
      <el-form :model="form" label-width="80px" @submit.prevent="login">
        <el-form-item label="用户 ID">
          <el-input-number v-model="form.userId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="租户 ID">
          <el-input-number v-model="form.tenantId" :min="1" style="width:100%" />
        </el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" style="width:100%">
          登录
        </el-button>
      </el-form>
      <div class="login-hint">开发模式：输入任意 ID 生成 JWT Token</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import authService from '@/services/authService'
import { setToken } from '@/services/http'

const router = useRouter()
const loading = ref(false)

const form = ref({ userId: 1, tenantId: 1 })

async function login() {
  loading.value = true
  try {
    const res = await authService.getToken(form.value.userId, form.value.tenantId)
    setToken(res.data.token)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.login-card {
  width: 400px;
}

.login-title {
  text-align: center;
  margin: 0;
  font-size: 20px;
}

.login-hint {
  text-align: center;
  font-size: 12px;
  color: #909399;
  margin-top: 12px;
}
</style>
