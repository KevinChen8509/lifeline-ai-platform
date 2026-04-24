import { ref, computed } from 'vue'

export interface RuleFormItem {
  ruleType: string
  condition: { field: string; operator: string; value: number }
  cooldownSeconds: number
  priority: number
  enabled: boolean
}

const defaultRule = (): RuleFormItem => ({
  ruleType: 'THRESHOLD',
  condition: { field: 'value', operator: 'GT', value: 0 },
  cooldownSeconds: 300,
  priority: 0,
  enabled: true
})

export function useRuleForm() {
  const rules = ref<RuleFormItem[]>([defaultRule()])

  const operators = [
    { label: '>', value: 'GT' },
    { label: '>=', value: 'GTE' },
    { label: '<', value: 'LT' },
    { label: '<=', value: 'LTE' },
    { label: '==', value: 'EQ' },
    { label: '!=', value: 'NEQ' }
  ]

  const ruleTypes = [
    { label: '阈值', value: 'THRESHOLD' },
    { label: '变化率', value: 'RATE_CHANGE' },
    { label: '设备离线', value: 'DEVICE_OFFLINE' },
    { label: '数据缺失', value: 'DATA_MISSING' }
  ]

  const hasConflict = ref(false)
  const conflictMessages = ref<string[]>([])

  function addRule() { rules.value.push(defaultRule()) }
  function removeRule(index: number) { rules.value.splice(index, 1) }

  function resetRules() {
    rules.value = [defaultRule()]
    hasConflict.value = false
    conflictMessages.value = []
  }

  const isValid = computed(() => rules.value.length > 0 && rules.value.every(r => r.condition.value !== undefined))

  return { rules, operators, ruleTypes, hasConflict, conflictMessages, addRule, removeRule, resetRules, isValid }
}
