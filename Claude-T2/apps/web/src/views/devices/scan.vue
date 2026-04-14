<template>
  <div style="padding: 24px;">
    <a-card title="扫码注册设备">
      <div style="max-width: 500px; margin: 40px auto; text-align: center;">
        <a-input-search
          v-model:value="qrData"
          placeholder="扫描或输入二维码数据 (LK://SN:TYPE:FACTORY)"
          enter-button="注册"
          size="large"
          @search="handleScan"
          :loading="loading"
        />

        <div v-if="result" style="margin-top: 24px; text-align: left;">
          <a-result
            :status="result.isNew ? 'success' : 'info'"
            :title="result.isNew ? '新设备已注册' : '设备已存在'"
          >
            <template #subTitle>
              <a-descriptions :column="1" bordered size="small">
                <a-descriptions-item label="设备名称">{{ result.device?.name }}</a-descriptions-item>
                <a-descriptions-item label="序列号">{{ result.device?.serialNumber }}</a-descriptions-item>
                <a-descriptions-item label="设备类型">{{ result.device?.deviceType || '-' }}</a-descriptions-item>
                <a-descriptions-item label="状态">{{ result.device?.status }}</a-descriptions-item>
              </a-descriptions>
            </template>
          </a-result>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { scanRegister } from '@/api/device';

const qrData = ref('');
const loading = ref(false);
const result = ref<any>(null);

const handleScan = async () => {
  if (!qrData.value) { message.warning('请输入二维码数据'); return; }
  loading.value = true;
  try {
    result.value = await scanRegister(qrData.value);
    message.success(result.value.isNew ? '新设备已注册' : '设备已存在');
  } catch { message.error('扫码注册失败'); }
  finally { loading.value = false; }
};
</script>
