<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="router.back()" text><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <h3>{{ isEdit ? '编辑订阅' : '创建订阅' }}</h3>
    </div>

    <!-- 三步 Stepper -->
    <el-steps :active="step" align-center class="stepper">
      <el-step title="基本信息" />
      <el-step title="规则配置" />
      <el-step title="确认提交" />
    </el-steps>

    <div class="step-content">
      <!-- Step 1: 基本信息 -->
      <div v-show="step === 0">
        <el-form ref="basicFormRef" :model="basicForm" :rules="basicRules" label-width="120px" class="step-form">
          <el-form-item label="订阅名称" prop="name">
            <el-input v-model="basicForm.name" placeholder="输入订阅名称" />
          </el-form-item>
          <el-form-item label="目标端点" prop="endpointId">
            <el-select v-model="basicForm.endpointId" placeholder="选择 Webhook 端点" style="width:100%">
              <el-option v-for="ep in endpointStore.endpoints" :key="ep.id" :label="ep.name" :value="ep.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="订阅类型" prop="subscriptionType">
            <el-radio-group v-model="basicForm.subscriptionType">
              <el-radio :value="0">设备级</el-radio>
              <el-radio :value="1">设备类型级</el-radio>
              <el-radio :value="2">分组级</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="目标" prop="targetId">
            <el-input-number v-model="basicForm.targetId" :min="1" placeholder="目标 ID" />
          </el-form-item>
          <el-form-item label="数据点">
            <el-select v-model="basicForm.dataPointIds" multiple placeholder="选择数据点（可选）" style="width:100%">
              <el-option v-for="dp in dataPoints" :key="dp.id" :label="`${dp.identifier} (${dp.dataType})`" :value="dp.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: 规则配置 -->
      <div v-show="step === 1">
        <RuleConflictWarning :conflicts="conflictMessages" />
        <div v-for="(rule, idx) in rules" :key="idx" class="rule-item">
          <el-card shadow="never">
            <div class="rule-header">
              <span>规则 #{{ idx + 1 }}</span>
              <el-button v-if="rules.length > 1" link type="danger" @click="removeRule(idx)">删除</el-button>
            </div>
            <el-form label-width="100px">
              <el-form-item label="规则类型">
                <el-select v-model="rule.ruleType" style="width:200px">
                  <el-option v-for="t in ruleTypes" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="条件">
                <div class="condition-row">
                  <el-input v-model="rule.condition.field" placeholder="字段" style="width:120px" />
                  <el-select v-model="rule.condition.operator" style="width:100px">
                    <el-option v-for="op in operators" :key="op.value" :label="op.label" :value="op.value" />
                  </el-select>
                  <el-input-number v-model="rule.condition.value" placeholder="阈值" />
                </div>
              </el-form-item>
              <el-form-item label="冷却时间(秒)">
                <el-input-number v-model="rule.cooldownSeconds" :min="0" :step="60" />
              </el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="rule.priority" style="width:140px">
                  <el-option label="Info" :value="0" />
                  <el-option label="Warning" :value="1" />
                  <el-option label="Critical" :value="2" />
                </el-select>
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="rule.enabled" />
              </el-form-item>
            </el-form>
          </el-card>
        </div>
        <el-button type="primary" plain @click="addRule()" class="add-rule-btn">+ 添加规则</el-button>
      </div>

      <!-- Step 3: 确认 -->
      <div v-show="step === 2">
        <el-descriptions title="订阅摘要" :column="2" border>
          <el-descriptions-item label="名称">{{ basicForm.name }}</el-descriptions-item>
          <el-descriptions-item label="端点">{{ selectedEndpointName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ ['设备级', '设备类型级', '分组级'][basicForm.subscriptionType] }}</el-descriptions-item>
          <el-descriptions-item label="目标 ID">{{ basicForm.targetId }}</el-descriptions-item>
          <el-descriptions-item label="数据点">{{ basicForm.dataPointIds?.join(', ') || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="规则数量">{{ rules.length }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="step-actions">
      <el-button v-if="step > 0" @click="step--">上一步</el-button>
      <el-button v-if="step < 2" type="primary" @click="nextStep">下一步</el-button>
      <el-button v-if="step === 2" type="success" :loading="submitting" @click="submit">提交</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useEndpointStore } from '@/stores/endpoint'
import { useSubscriptionStore } from '@/stores/subscription'
import { useRuleForm } from '@/composables/useRuleForm'
import deviceService from '@/services/deviceService'
import RuleConflictWarning from '@/components/RuleConflictWarning.vue'
import type { DeviceDataPoint } from '@/types/device'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const endpointStore = useEndpointStore()
const subStore = useSubscriptionStore()
const { rules, operators, ruleTypes, addRule, removeRule, resetRules } = useRuleForm()

const step = ref(0)
const submitting = ref(false)
const isEdit = computed(() => !!route.query.edit)
const dataPoints = ref<DeviceDataPoint[]>([])
const conflictMessages = ref<string[]>([])
const basicFormRef = ref<FormInstance>()
const basicForm = ref({
  name: '',
  endpointId: null as number | null,
  subscriptionType: 0,
  targetId: null as number | null,
  dataPointIds: [] as number[]
})

const basicRules: FormRules = {
  name: [{ required: true, message: '请输入订阅名称', trigger: 'blur' }],
  endpointId: [{ required: true, message: '请选择端点', trigger: 'change' }],
  subscriptionType: [{ required: true, message: '请选择订阅类型', trigger: 'change' }],
  targetId: [{ required: true, message: '请输入目标 ID', trigger: 'blur' }],
}

const selectedEndpointName = computed(() => {
  const ep = endpointStore.endpoints.find(e => e.id === basicForm.value.endpointId)
  return ep?.name || '-'
})

async function nextStep() {
  if (step.value === 0) {
    await basicFormRef.value?.validate()
    // 加载数据点
    if (basicForm.value.targetId) {
      try {
        const res = await deviceService.getDataPoints(basicForm.value.targetId)
        dataPoints.value = res.data || []
      } catch { /* 设备类型级/分组级可能无法加载数据点 */ }
    }
    // 检查规则冲突（仅设备级订阅）
    if (basicForm.value.subscriptionType === 0 && basicForm.value.targetId) {
      try {
        const conflicts = await subStore.checkConflicts(
          basicForm.value.targetId,
          basicForm.value.dataPointIds.length > 0 ? basicForm.value.dataPointIds : undefined,
          route.query.edit ? Number(route.query.edit) : undefined
        )
        conflictMessages.value = conflicts.length > 0
          ? conflicts.map((c: any) => `规则 ID ${c.id}: ${c.conditionJson} 可能与已有订阅重叠`)
          : []
      } catch {
        conflictMessages.value = []
      }
    } else {
      conflictMessages.value = []
    }
  }
  step.value++
}

async function submit() {
  submitting.value = true
  try {
    const payload = {
      name: basicForm.value.name,
      endpointId: basicForm.value.endpointId!,
      subscriptionType: basicForm.value.subscriptionType,
      targetId: basicForm.value.targetId!,
      dataPointIds: basicForm.value.dataPointIds.length > 0 ? basicForm.value.dataPointIds : undefined,
      rules: rules.map(r => ({
        ruleType: ruleTypeToNumber(r.ruleType),
        conditionJson: JSON.stringify({ field: r.condition.field, operator: r.condition.operator, value: r.condition.value }),
        cooldownSeconds: r.cooldownSeconds,
        priority: r.priority,
        enabled: r.enabled
      }))
    }

    if (isEdit.value) {
      await subStore.updateSubscription(Number(route.query.edit), payload)
      ElMessage.success('订阅已更新')
    } else {
      await subStore.createSubscription(payload)
      ElMessage.success('订阅创建成功')
    }
    router.push({ name: 'SubscriptionList' })
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function ruleTypeToNumber(type: string): number {
  const map: Record<string, number> = { THRESHOLD: 0, RATE_CHANGE: 1, DEVICE_OFFLINE: 2, DATA_MISSING: 3 }
  return map[type] ?? 0
}

onMounted(() => {
  endpointStore.fetchEndpoints()
})
</script>

<style scoped>
.page-container { padding: 20px; }

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.page-header h3 { margin: 0; }

.stepper { margin-bottom: 30px; }

.step-content {
  max-width: 800px;
  margin: 0 auto;
}

.step-form { max-width: 600px; }

.rule-item { margin-bottom: 16px; }

.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
}

.condition-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.add-rule-btn { margin-top: 12px; }

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 30px;
}
</style>
