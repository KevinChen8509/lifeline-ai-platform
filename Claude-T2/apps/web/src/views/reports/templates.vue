<template>
  <div style="padding: 24px;">
    <a-card title="报告模板">
      <template #extra>
        <a-button type="primary" @click="showCreateModal">创建模板</a-button>
      </template>

      <a-table :columns="columns" :data-source="templates" :loading="loading" row-key="id" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag>{{ record.type }}</a-tag>
          </template>
          <template v-if="column.key === 'isDefault'">
            <a-tag :color="record.isDefault ? 'blue' : 'default'">{{ record.isDefault ? '默认' : '自定义' }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
              <a-popconfirm v-if="!record.isDefault" title="确定删除?" @confirm="handleDelete(record)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" :title="editingId ? '编辑模板' : '创建模板'" @ok="handleSave">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="类型" required>
          <a-select v-model:value="form.type">
            <a-select-option value="DAILY">日报</a-select-option>
            <a-select-option value="WEEKLY">周报</a-select-option>
            <a-select-option value="MONTHLY">月报</a-select-option>
            <a-select-option value="CUSTOM">自定义</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目ID">
          <a-input v-model:value="form.projectId" placeholder="留空为全局模板" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listTemplates, createTemplate, updateTemplate, deleteTemplate } from '@/api/report';

const templates = ref<any[]>([]);
const loading = ref(false);
const modalVisible = ref(false);
const editingId = ref('');
const form = reactive({ name: '', type: 'DAILY', projectId: '' });

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '类型', key: 'type', width: 100 },
  { title: '类型', key: 'isDefault', width: 80 },
  { title: '操作', key: 'action', width: 150 },
];

const loadTemplates = async () => { loading.value = true; try { templates.value = await listTemplates(); } finally { loading.value = false; } };

const showCreateModal = () => { editingId.value = ''; form.name = ''; form.type = 'DAILY'; form.projectId = ''; modalVisible.value = true; };
const showEditModal = (record: any) => { editingId.value = record.id; form.name = record.name; form.type = record.type; modalVisible.value = true; };

const handleSave = async () => {
  try {
    if (editingId.value) {
      await updateTemplate(editingId.value, { name: form.name });
    } else {
      await createTemplate(form);
    }
    message.success('保存成功');
    modalVisible.value = false;
    loadTemplates();
  } catch { message.error('保存失败'); }
};

const handleDelete = async (record: any) => {
  try { await deleteTemplate(record.id); message.success('已删除'); loadTemplates(); } catch { message.error('删除失败'); }
};

onMounted(() => { loadTemplates(); });
</script>
