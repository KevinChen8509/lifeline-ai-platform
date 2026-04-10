<template>
  <div class="audit-log-page">
    <a-card :bordered="false">
      <!-- 搜索表单 -->
      <a-form
        layout="inline"
        :model="searchForm"
        class="search-form"
        @finish="handleSearch"
      >
        <a-form-item label="时间范围">
          <a-range-picker
            v-model:value="searchForm.dateRange"
            :show-time="{ format: 'HH:mm:ss' }"
            format="YYYY-MM-DD HH:mm:ss"
            :placeholder="['开始时间', '结束时间']"
            style="width: 380px"
          />
        </a-form-item>

        <a-form-item label="用户ID">
          <a-input
            v-model:value="searchForm.userId"
            placeholder="输入用户ID"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>

        <a-form-item label="操作类型">
          <a-select
            v-model:value="searchForm.action"
            placeholder="选择操作类型"
            allow-clear
            style="width: 150px"
          >
            <a-select-option
              v-for="item in actionOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="目标类型">
          <a-select
            v-model:value="searchForm.targetType"
            placeholder="选择目标类型"
            allow-clear
            style="width: 150px"
          >
            <a-select-option
              v-for="item in targetTypeOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="loading">
              查询
            </a-button>
            <a-button @click="handleReset">重置</a-button>
            <a-button @click="handleExport" :loading="exporting">
              导出CSV
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <!-- 数据表格 -->
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
        class="audit-log-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operator'">
            <span v-if="record.operator">
              {{ record.operator.name }} ({{ record.operator.username }})
            </span>
            <span v-else class="text-gray-400">系统</span>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-tag :color="getActionColor(record.action)">
              {{ getActionLabel(record.action) }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'targetType'">
            <a-tag color="blue">{{ getTargetTypeLabel(record.targetType) }}</a-tag>
          </template>

          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>

          <template v-else-if="column.key === 'description'">
            <a-tooltip v-if="record.description && record.description.length > 50">
              <template #title>{{ record.description }}</template>
              <span>{{ record.description.substring(0, 50) }}...</span>
            </a-tooltip>
            <span v-else>{{ record.description || '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import dayjs, { Dayjs } from 'dayjs';
import {
  getAuditLogs,
  exportAuditLogs,
  actionOptions,
  targetTypeOptions,
  type AuditLogItem,
} from '@/api/system/auditLog';

// 搜索表单
const searchForm = reactive({
  dateRange: null as [Dayjs, Dayjs] | null,
  userId: '',
  action: '',
  targetType: '',
});

// 表格数据
const tableData = ref<AuditLogItem[]>([]);
const loading = ref(false);
const exporting = ref(false);

// 分页
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
});

// 表格列定义
const columns: TableColumnsType = [
  {
    title: '时间',
    key: 'createdAt',
    dataIndex: 'createdAt',
    width: 180,
    sorter: true,
  },
  {
    title: '操作者',
    key: 'operator',
    width: 150,
  },
  {
    title: '操作类型',
    key: 'action',
    dataIndex: 'action',
    width: 120,
  },
  {
    title: '目标类型',
    key: 'targetType',
    dataIndex: 'targetType',
    width: 100,
  },
  {
    title: '目标ID',
    dataIndex: 'targetId',
    width: 120,
    ellipsis: true,
  },
  {
    title: 'IP地址',
    dataIndex: 'ipAddress',
    width: 140,
  },
  {
    title: '描述',
    key: 'description',
    dataIndex: 'description',
    ellipsis: true,
  },
];

// 获取操作类型颜色
function getActionColor(action: string): string {
  const colorMap: Record<string, string> = {
    LOGIN: 'green',
    LOGOUT: 'default',
    LOGIN_FAILED: 'red',
    CREATE_USER: 'green',
    UPDATE_USER: 'blue',
    DELETE_USER: 'red',
    ASSIGN_ROLE: 'purple',
    UPDATE_STATUS: 'orange',
  };
  return colorMap[action] || 'default';
}

// 获取操作类型标签
function getActionLabel(action: string): string {
  const labelMap: Record<string, string> = {
    LOGIN: '登录',
    LOGOUT: '登出',
    LOGIN_FAILED: '登录失败',
    CREATE_USER: '创建用户',
    UPDATE_USER: '更新用户',
    DELETE_USER: '删除用户',
    ASSIGN_ROLE: '分配角色',
    UPDATE_STATUS: '更新状态',
  };
  return labelMap[action] || action;
}

// 获取目标类型标签
function getTargetTypeLabel(targetType: string): string {
  const labelMap: Record<string, string> = {
    User: '用户',
    Role: '角色',
    Project: '项目',
    Device: '设备',
    Model: '模型',
    Alert: '预警',
    ApiKey: 'API密钥',
    System: '系统',
  };
  return labelMap[targetType] || targetType;
}

// 格式化日期时间
function formatDateTime(date: string): string {
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss');
}

// 加载数据
async function loadData() {
  loading.value = true;
  try {
    const params: Record<string, any> = {
      page: pagination.current,
      pageSize: pagination.pageSize,
    };

    if (searchForm.dateRange) {
      params.startTime = searchForm.dateRange[0].toISOString();
      params.endTime = searchForm.dateRange[1].toISOString();
    }

    if (searchForm.userId) {
      params.userId = searchForm.userId;
    }

    if (searchForm.action) {
      params.action = searchForm.action;
    }

    if (searchForm.targetType) {
      params.targetType = searchForm.targetType;
    }

    const response = await getAuditLogs(params);
    tableData.value = response.items;
    pagination.total = response.total;
  } catch (error: any) {
    message.error(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

// 搜索
function handleSearch() {
  pagination.current = 1;
  loadData();
}

// 重置
function handleReset() {
  searchForm.dateRange = null;
  searchForm.userId = '';
  searchForm.action = '';
  searchForm.targetType = '';
  pagination.current = 1;
  loadData();
}

// 导出
async function handleExport() {
  exporting.value = true;
  try {
    const params: Record<string, any> = {};

    if (searchForm.dateRange) {
      params.startTime = searchForm.dateRange[0].toISOString();
      params.endTime = searchForm.dateRange[1].toISOString();
    }

    if (searchForm.userId) {
      params.userId = searchForm.userId;
    }

    if (searchForm.action) {
      params.action = searchForm.action;
    }

    if (searchForm.targetType) {
      params.targetType = searchForm.targetType;
    }

    const blob = await exportAuditLogs(params);

    // 创建下载链接
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `audit-logs-${dayjs().format('YYYY-MM-DD')}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);

    message.success('导出成功');
  } catch (error: any) {
    message.error(error.message || '导出失败');
  } finally {
    exporting.value = false;
  }
}

// 表格变化（分页、排序）
function handleTableChange(pag: any) {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
  loadData();
}

// 初始化
onMounted(() => {
  loadData();
});
</script>

<style scoped>
.audit-log-page {
  padding: 24px;
}

.search-form {
  margin-bottom: 16px;
}

.audit-log-table {
  margin-top: 16px;
}

.text-gray-400 {
  color: #9ca3af;
}
</style>
