import { useEffect, useRef, useState } from 'react'
import { Card, Chip, Spinner, Button } from '@heroui/react'
import {
  getStatus, updateLLMConfig, addDatabaseConfig, removeDatabaseConfig,
  testDatabaseConfig, autoTrain, clearKnowledge, retrainKnowledge,
  type StatusResponse, type DBConfigParams,
} from '../api/client'
import { useNavigate } from '../App'

type DBType = 'sqlite' | 'postgresql' | 'clickhouse'

const DB_TYPES: { value: DBType; label: string }[] = [
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'sqlite', label: 'SQLite' },
  { value: 'clickhouse', label: 'ClickHouse' },
]

const DB_DEFAULT_PORTS: Record<DBType, number> = {
  postgresql: 5432,
  clickhouse: 8123,
  sqlite: 0,
}

interface GuideState {
  step: 'idle' | 'saved' | 'training' | 'trained' | 'failed'
  dbName: string
  tablesCount: number
  trainResult: string
}

const emptyForm = (type: DBType = 'postgresql'): DBConfigParams => ({
  name: '',
  type,
  host: type === 'sqlite' ? '' : 'localhost',
  port: DB_DEFAULT_PORTS[type],
  username: type === 'sqlite' ? '' : 'postgres',
  password: '',
  database: '',
  schema: 'public',
})

export default function StatusPage() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const guideRef = useRef<HTMLDivElement>(null)

  // LLM 编辑
  const [editingLLM, setEditingLLM] = useState(false)
  const [savingLLM, setSavingLLM] = useState(false)
  const [llmBaseUrl, setLlmBaseUrl] = useState('')
  const [llmApiKey, setLlmApiKey] = useState('')
  const [llmModel, setLlmModel] = useState('')
  const [saveMsg, setSaveMsg] = useState('')

  // 数据库配置
  const [showDbForm, setShowDbForm] = useState(false)
  const [editingDbName, setEditingDbName] = useState<string | null>(null)
  const [dbForm, setDbForm] = useState<DBConfigParams>(emptyForm())
  const [dbSaving, setDbSaving] = useState(false)
  const [dbTesting, setDbTesting] = useState(false)
  const [dbTestResult, setDbTestResult] = useState<{ connected: boolean; tables_count: number; error: string | null } | null>(null)
  const [dbMsg, setDbMsg] = useState('')

  // 引导流程
  const [guide, setGuide] = useState<GuideState>({ step: 'idle', dbName: '', tablesCount: 0, trainResult: '' })

  useEffect(() => {
    loadStatus()
  }, [])

  // 引导出现时自动滚动
  useEffect(() => {
    if (guide.step !== 'idle') {
      setTimeout(() => guideRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100)
    }
  }, [guide.step])

  const loadStatus = async () => {
    setLoading(true)
    setError('')
    try {
      const s = await getStatus()
      setStatus(s)
      setLlmBaseUrl(s.llm.base_url)
      setLlmModel(s.llm.model)
      setLlmApiKey('')
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  // ── LLM 保存 ──────────────────────────────────────────

  const handleSaveLLM = async () => {
    setSavingLLM(true)
    setSaveMsg('')
    try {
      const params: Record<string, string> = {}
      if (llmBaseUrl) params.base_url = llmBaseUrl
      if (llmApiKey) params.api_key = llmApiKey
      if (llmModel) params.model = llmModel
      await updateLLMConfig(params)
      setEditingLLM(false)
      setLlmApiKey('')
      setSaveMsg('LLM 配置已更新并生效')
      await loadStatus()
      setTimeout(() => setSaveMsg(''), 3000)
    } catch (err) {
      setSaveMsg(`保存失败: ${err instanceof Error ? err.message : String(err)}`)
    } finally {
      setSavingLLM(false)
    }
  }

  // ── 数据库操作 ─────────────────────────────────────────

  const handleDbTypeChange = (type: DBType) => {
    setDbForm({ ...emptyForm(type), type })
    setDbTestResult(null)
  }

  const handleTestDb = async () => {
    setDbTesting(true)
    setDbTestResult(null)
    try {
      const result = await testDatabaseConfig(dbForm)
      setDbTestResult(result)
    } catch (err) {
      setDbTestResult({ connected: false, tables_count: 0, error: err instanceof Error ? err.message : String(err) })
    } finally {
      setDbTesting(false)
    }
  }

  const handleSaveDb = async () => {
    if (!dbForm.name.trim()) {
      setDbMsg('请填写数据库标识名称')
      return
    }
    setDbSaving(true)
    setDbMsg('')
    setDbTestResult(null)
    try {
      const result = await addDatabaseConfig(dbForm)
      if (!result.connected) {
        setDbMsg(result.error || '连接失败')
        setDbSaving(false)
        return
      }
      setShowDbForm(false)
      setEditingDbName(null)
      setDbMsg('')
      await loadStatus()

      // 启动引导流程
      setGuide({
        step: 'saved',
        dbName: result.db_name,
        tablesCount: result.tables_count,
        trainResult: '',
      })
    } catch (err) {
      setDbMsg(`保存失败: ${err instanceof Error ? err.message : String(err)}`)
    } finally {
      setDbSaving(false)
    }
  }

  const handleEditDb = (db: { name: string; type: string; host: string; port: number; username: string; database: string; schema: string }) => {
    setDbForm({
      name: db.name,
      type: db.type as DBType,
      host: db.host,
      port: db.port || DB_DEFAULT_PORTS[db.type as DBType] || 5432,
      username: db.username || '',
      password: '',
      database: db.database || '',
      schema: db.schema || 'public',
    })
    setEditingDbName(db.name)
    setShowDbForm(true)
    setDbTestResult(null)
    setDbMsg('')
    setGuide({ step: 'idle', dbName: '', tablesCount: 0, trainResult: '' })
  }

  const handleDeleteDb = async (name: string) => {
    if (!confirm(`确定删除数据库 "${name}"？删除后将无法查询该数据库。`)) return
    try {
      await removeDatabaseConfig(name)
      await loadStatus()
    } catch (err) {
      setDbMsg(`删除失败: ${err instanceof Error ? err.message : String(err)}`)
    }
  }

  const handleCancelDbForm = () => {
    setShowDbForm(false)
    setEditingDbName(null)
    setDbForm(emptyForm())
    setDbTestResult(null)
    setDbMsg('')
  }

  // ── 引导流程 ───────────────────────────────────────────

  const handleGuideTrain = async () => {
    setGuide(prev => ({ ...prev, step: 'training' }))
    try {
      const res = await autoTrain()
      const summaries = Object.entries(res as Record<string, { tables: number; columns: number; trained: number; status: string }>)
        .map(([db, info]) => `${db}: ${info.trained}/${info.tables} 表, ${info.columns} 列 (${info.status})`)
        .join('\n')
      setGuide(prev => ({ ...prev, step: 'trained', trainResult: summaries }))
      await loadStatus()
    } catch (err) {
      setGuide(prev => ({
        ...prev,
        step: 'failed',
        trainResult: err instanceof Error ? err.message : String(err),
      }))
    }
  }

  const closeGuide = () => {
    setGuide({ step: 'idle', dbName: '', tablesCount: 0, trainResult: '' })
  }

  // ── 知识库操作 ──────────────────────────────────────────

  const [kbBusy, setKbBusy] = useState(false)

  const handleClearKnowledge = async (type: string, label: string) => {
    if (!confirm(`确定清空${label}？此操作不可撤销。`)) return
    setKbBusy(true)
    try {
      await clearKnowledge(type)
      await loadStatus()
    } catch (err) {
      alert(`清空失败: ${err instanceof Error ? err.message : String(err)}`)
    } finally {
      setKbBusy(false)
    }
  }

  const handleRetrain = async () => {
    if (!confirm('确定重新训练？将清空现有知识库并重新采集所有数据库 Schema。')) return
    setKbBusy(true)
    try {
      const res = await retrainKnowledge()
      await loadStatus()
      const summary = Object.entries(res.train_result)
        .map(([db, info]) => `${db}: ${info.trained}/${info.tables} 表, ${info.columns} 列 (${info.status})`)
        .join('\n')
      alert(`重新训练完成\n\n清空 ${res.cleared} 条旧数据\n\n${summary}`)
    } catch (err) {
      alert(`训练失败: ${err instanceof Error ? err.message : String(err)}`)
    } finally {
      setKbBusy(false)
    }
  }

  // ── 步骤渲染 ───────────────────────────────────────────

  const renderStep = (idx: number, label: string, state: 'done' | 'active' | 'pending', isLast = false) => (
    <div className="flex items-start" key={label}>
      <div className="flex flex-col items-center">
        <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold shrink-0 ${
          state === 'done' ? 'bg-green-500 text-white' :
          state === 'active' ? 'bg-indigo-600 text-white ring-4 ring-indigo-100' :
          'bg-gray-200 text-gray-400'
        }`}>
          {state === 'done' ? '\u2713' : idx + 1}
        </div>
        {!isLast && (
          <div className={`w-0.5 h-6 ${state === 'done' ? 'bg-green-400' : 'bg-gray-200'}`} />
        )}
      </div>
      <div className="ml-3 pb-4">
        <span className={`text-sm font-medium ${
          state === 'done' ? 'text-green-600' :
          state === 'active' ? 'text-indigo-700' :
          'text-gray-400'
        }`}>
          {label}
        </span>
      </div>
    </div>
  )

  // ── 渲染 ────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Spinner size="lg" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="p-6 max-w-3xl mx-auto">
        <Card>
          <Card.Content>
            <p className="text-red-500">加载状态失败: {error}</p>
          </Card.Content>
        </Card>
      </div>
    )
  }

  if (!status) return null

  const currentStep = guide.step === 'idle' ? -1 :
    guide.step === 'saved' ? 1 :
    guide.step === 'training' ? 1 :
    guide.step === 'trained' ? 2 :
    guide.step === 'failed' ? 1 : -1

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="max-w-3xl mx-auto space-y-4">
      <h2 className="text-xl font-semibold text-gray-800">系统状态</h2>

      {/* LLM 连接 */}
      <Card>
        <Card.Header className="flex-row items-center justify-between">
          <Card.Title>LLM 连接</Card.Title>
          {!editingLLM ? (
            <Button size="sm" variant="ghost" onPress={() => setEditingLLM(true)}>
              编辑
            </Button>
          ) : (
            <Button size="sm" variant="ghost" onPress={() => { setEditingLLM(false); setLlmApiKey('') }}>
              取消
            </Button>
          )}
        </Card.Header>
        <Card.Content className="space-y-2">
          {!editingLLM ? (
            <>
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-500 w-20">模型:</span>
                <Chip size="sm" variant="soft" color="accent">{status.llm.model}</Chip>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-500 w-20">API 地址:</span>
                <span className="text-sm break-all">{status.llm.base_url}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-500 w-20">状态:</span>
                <Chip size="sm" color={status.llm.connected ? 'success' : 'danger'} variant="soft">
                  {status.llm.connected ? '已连接' : '未连接'}
                </Chip>
              </div>
            </>
          ) : (
            <div className="space-y-3">
              <div>
                <label className="block text-sm text-gray-600 mb-1">API 地址 (Base URL)</label>
                <input className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={llmBaseUrl} onChange={(e) => setLlmBaseUrl(e.target.value)} placeholder="https://ark.cn-beijing.volces.com/api/v3" />
              </div>
              <div>
                <label className="block text-sm text-gray-600 mb-1">API Key</label>
                <input type="password" className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={llmApiKey} onChange={(e) => setLlmApiKey(e.target.value)} placeholder="留空表示不修改" />
              </div>
              <div>
                <label className="block text-sm text-gray-600 mb-1">模型 / Endpoint ID</label>
                <input className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={llmModel} onChange={(e) => setLlmModel(e.target.value)} placeholder="ep-xxxx 或模型名称" />
              </div>
              <Button variant="primary" isDisabled={savingLLM || (!llmBaseUrl && !llmApiKey && !llmModel)} onPress={handleSaveLLM}>
                {savingLLM ? '保存中...' : '保存并生效'}
              </Button>
            </div>
          )}
          {saveMsg && (
            <div className="mt-2">
              <Chip size="sm" color={saveMsg.includes('失败') ? 'danger' : 'success'} variant="soft">
                {saveMsg}
              </Chip>
            </div>
          )}
        </Card.Content>
      </Card>

      {/* 数据库配置 */}
      <Card>
        <Card.Header className="flex-row items-center justify-between">
          <Card.Title>数据库配置</Card.Title>
          {!showDbForm && (
            <Button size="sm" variant="ghost" onPress={() => { setShowDbForm(true); setEditingDbName(null); setDbForm(emptyForm()); setDbTestResult(null) }}>
              + 添加数据库
            </Button>
          )}
        </Card.Header>
        <Card.Content className="space-y-3">
          {status.databases.length === 0 && !showDbForm && (
            <div className="text-center py-8 text-gray-400">
              <p className="text-sm">暂无数据库配置</p>
              <p className="text-xs mt-1">点击上方"添加数据库"开始配置</p>
            </div>
          )}
          {status.databases.map((db) => (
            <div key={db.name} className="flex items-center gap-2 p-3 rounded-md bg-gray-50 border border-gray-100">
              <Chip size="sm" variant="solid" color={
                db.type === 'postgresql' ? 'accent' :
                db.type === 'clickhouse' ? 'warning' : 'default'
              }>
                {db.type}
              </Chip>
              <div className="flex-1 min-w-0">
                <span className="text-sm font-medium">{db.name}</span>
                <span className="text-xs text-gray-400 ml-2">{db.host}{db.port ? `:${db.port}` : ''}</span>
                {db.database && <span className="text-xs text-gray-400 ml-1">/ {db.database}{db.schema && db.schema !== 'public' ? `.${db.schema}` : ''}</span>}
              </div>
              <Button size="sm" variant="ghost" onPress={() => handleEditDb(db)}>编辑</Button>
              <Button size="sm" variant="ghost" color="danger" onPress={() => handleDeleteDb(db.name)}>删除</Button>
            </div>
          ))}

          {/* 添加/编辑数据库表单 */}
          {showDbForm && (
            <div className="border border-indigo-200 rounded-lg p-4 space-y-3 bg-indigo-50/30">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-semibold text-indigo-700">
                  {editingDbName ? `编辑: ${editingDbName}` : '添加新数据库'}
                </h4>
                <Button size="sm" variant="ghost" onPress={handleCancelDbForm}>取消</Button>
              </div>

              <div>
                <label className="block text-sm text-gray-600 mb-1">数据库类型</label>
                <select
                  className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-white"
                  value={dbForm.type}
                  onChange={(e) => handleDbTypeChange(e.target.value as DBType)}
                  disabled={!!editingDbName}
                >
                  {DB_TYPES.map(t => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm text-gray-600 mb-1">标识名称</label>
                <input
                  className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                  value={dbForm.name}
                  onChange={(e) => setDbForm({ ...dbForm, name: e.target.value })}
                  placeholder="如: my_pg, my_ch"
                  disabled={!!editingDbName}
                />
              </div>

              {dbForm.type === 'sqlite' && (
                <div>
                  <label className="block text-sm text-gray-600 mb-1">数据库文件路径</label>
                  <input
                    className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                    value={dbForm.database}
                    onChange={(e) => setDbForm({ ...dbForm, database: e.target.value })}
                    placeholder="./data/my.db"
                  />
                </div>
              )}

              {dbForm.type !== 'sqlite' && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-sm text-gray-600 mb-1">主机地址</label>
                      <input className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={dbForm.host} onChange={(e) => setDbForm({ ...dbForm, host: e.target.value })} placeholder="localhost" />
                    </div>
                    <div>
                      <label className="block text-sm text-gray-600 mb-1">端口</label>
                      <input type="number" className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={dbForm.port} onChange={(e) => setDbForm({ ...dbForm, port: parseInt(e.target.value) || 0 })} placeholder={String(DB_DEFAULT_PORTS[dbForm.type])} />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-sm text-gray-600 mb-1">用户名</label>
                      <input className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={dbForm.username} onChange={(e) => setDbForm({ ...dbForm, username: e.target.value })} placeholder="postgres" />
                    </div>
                    <div>
                      <label className="block text-sm text-gray-600 mb-1">密码</label>
                      <input type="password" className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={dbForm.password} onChange={(e) => setDbForm({ ...dbForm, password: e.target.value })} placeholder={editingDbName ? '留空表示不修改' : '输入密码'} />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">数据库名</label>
                    <input className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={dbForm.database} onChange={(e) => setDbForm({ ...dbForm, database: e.target.value })} placeholder="mydb" />
                  </div>
                  {dbForm.type === 'postgresql' && (
                    <div>
                      <label className="block text-sm text-gray-600 mb-1">Schema</label>
                      <input className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300" value={dbForm.schema} onChange={(e) => setDbForm({ ...dbForm, schema: e.target.value })} placeholder="public" />
                    </div>
                  )}
                </>
              )}

              {dbTestResult && (
                <div className={`p-3 rounded-md text-sm flex items-center gap-2 ${
                  dbTestResult.connected ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'
                }`}>
                  <span className="text-base">{dbTestResult.connected ? '\u2713' : '\u2717'}</span>
                  {dbTestResult.connected
                    ? `连接成功，发现 ${dbTestResult.tables_count} 张表`
                    : `连接失败: ${dbTestResult.error}`}
                </div>
              )}

              <div className="flex items-center gap-2 pt-1">
                <Button size="sm" variant="outline" isDisabled={dbTesting || !dbForm.name} onPress={handleTestDb}>
                  {dbTesting ? '测试中...' : '测试连接'}
                </Button>
                <Button size="sm" variant="primary" isDisabled={dbSaving || !dbForm.name} onPress={handleSaveDb}>
                  {dbSaving ? '保存中...' : '保存并生效'}
                </Button>
              </div>

              {dbMsg && (
                <div className={`p-3 rounded-md text-sm ${dbMsg.includes('失败') ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'}`}>
                  {dbMsg}
                </div>
              )}
            </div>
          )}
        </Card.Content>
      </Card>

      {/* 引导向导 — 保存数据库后出现 */}
      {guide.step !== 'idle' && (
        <div ref={guideRef} className="border-2 border-indigo-300 rounded-xl bg-gradient-to-b from-indigo-50 to-white shadow-sm">
          <div className="p-5 space-y-4">
            {/* 标题 */}
            <div className="flex items-center justify-between">
              <h3 className="text-base font-semibold text-indigo-800">
                配置向导 — {guide.dbName}
              </h3>
              <Button size="sm" variant="ghost" onPress={closeGuide}>关闭</Button>
            </div>

            {/* 竖向步骤条 */}
            <div className="pl-1">
              {/* Step 1: 连接数据库 */}
              {renderStep(0, `连接数据库 ${guide.dbName}`, 'done')}
              <div className="ml-4 -mt-2 mb-3 pl-5">
                <div className="bg-green-50 border border-green-200 rounded-md px-3 py-2 text-sm text-green-700 flex items-center gap-2">
                  <span className="text-base">{'\u2713'}</span>
                  连接成功，发现 {guide.tablesCount} 张表
                </div>
              </div>

              {/* Step 2: 训练知识库 */}
              {renderStep(1, '训练知识库', currentStep > 1 ? 'done' : currentStep === 1 ? 'active' : 'pending')}
              <div className="ml-4 -mt-2 mb-3 pl-5">
                {guide.step === 'saved' && (
                  <div className="space-y-3">
                    <p className="text-sm text-gray-600">
                      训练后系统将学习数据库表结构和字段含义，可以更准确地理解自然语言并生成 SQL。
                    </p>
                    <Button size="sm" variant="primary" onPress={handleGuideTrain}>
                      立即训练知识库
                    </Button>
                  </div>
                )}
                {guide.step === 'training' && (
                  <div className="flex items-center gap-3 py-2">
                    <Spinner size="sm" />
                    <span className="text-sm text-indigo-600">正在采集表结构并训练知识库，请稍候...</span>
                  </div>
                )}
                {guide.step === 'failed' && (
                  <div className="space-y-2">
                    <div className="bg-red-50 border border-red-200 rounded-md px-3 py-2 text-sm text-red-700">
                      训练失败: {guide.trainResult}
                    </div>
                    <Button size="sm" variant="outline" onPress={handleGuideTrain}>重试</Button>
                  </div>
                )}
                {(guide.step === 'trained') && (
                  <div className="space-y-2">
                    <div className="bg-green-50 border border-green-200 rounded-md px-3 py-2 text-sm text-green-700 flex items-center gap-2">
                      <span className="text-base">{'\u2713'}</span>
                      知识库训练完成
                    </div>
                    <div className="bg-white border border-gray-200 rounded-md px-3 py-2 text-xs text-gray-600 whitespace-pre-line">
                      {guide.trainResult}
                    </div>
                  </div>
                )}
              </div>

              {/* Step 3: 开始对话 */}
              {renderStep(2, '开始对话查询', guide.step === 'trained' ? 'active' : 'pending', true)}
              {guide.step === 'trained' && (
                <div className="ml-4 -mt-2 pl-5">
                  <div className="space-y-3">
                    <p className="text-sm text-gray-600">
                      知识库已就绪，现在可以使用自然语言查询 <strong>{guide.dbName}</strong> 数据库了。
                    </p>
                    <Button
                      variant="primary"
                      onPress={() => {
                        closeGuide()
                        navigate('chat')
                      }}
                    >
                      去对话查询 {'\u2192'}
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 知识库统计 */}
      <Card>
        <Card.Header className="flex-row items-center justify-between">
          <Card.Title>知识库统计</Card.Title>
          <Button
            size="sm"
            variant="primary"
            isDisabled={kbBusy}
            onPress={handleRetrain}
          >
            {kbBusy ? '处理中...' : '重新训练'}
          </Button>
        </Card.Header>
        <Card.Content>
          <div className="grid grid-cols-3 gap-4">
            {([
              { count: status.knowledge.ddl_count, label: 'DDL', type: 'ddl', color: 'text-blue-600' },
              { count: status.knowledge.doc_count, label: '文档', type: 'documentation', color: 'text-emerald-600' },
              { count: status.knowledge.question_sql_count, label: '问答对', type: 'question_sql', color: 'text-amber-600' },
            ] as const).map((item) => (
              <div key={item.type} className="text-center">
                <p className={`text-2xl font-semibold ${item.color}`}>{item.count}</p>
                <p className="text-sm text-gray-500">{item.label}</p>
                <Button
                  size="sm"
                  variant="ghost"
                  className="text-xs mt-1 text-gray-400"
                  isDisabled={kbBusy || item.count === 0}
                  onPress={() => handleClearKnowledge(item.type, item.label)}
                >
                  清空
                </Button>
              </div>
            ))}
          </div>
        </Card.Content>
      </Card>

      {/* 安全模式 */}
      <Card>
        <Card.Header>
          <Card.Title>安全模式</Card.Title>
        </Card.Header>
        <Card.Content>
          <Chip size="sm" variant="soft" color="warning">
            {status.security_mode}
          </Chip>
        </Card.Content>
      </Card>
      </div>
    </div>
  )
}
