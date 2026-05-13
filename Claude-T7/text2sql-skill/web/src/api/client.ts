import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 180000,
})

// ── 类型 ──────────────────────────────────────────────

export interface AskResponse {
  question: string
  sql: string
  status: string
  db_name: string
  db_type: string
  elapsed_ms: number
  data: Record<string, unknown>[]
  row_count: number
  truncated: boolean
  chart_recommendation: {
    chart_type: string
    x_column: string
    y_column: string
    color_column: string
    reason: string
  } | null
  error: string | null
  autofixed: boolean
  autofix_attempts: number
}

export interface StatusResponse {
  llm: { model: string; base_url: string; connected: boolean }
  databases: { name: string; type: string; host: string; port: number; username: string; database: string; schema: string }[]
  knowledge: { ddl_count: number; doc_count: number; question_sql_count: number }
  security_mode: string
}

export interface TrainResult {
  item_id: string
  added: boolean
  message: string
}

export interface FeedbackResult {
  accepted: boolean
  action: string
  reason: string
}

export interface ConversationInfo {
  id: string
  db_name: string
}

export interface ConversationDetail {
  id: string
  db_name: string
  turns: { question: string; sql: string; answer: string }[]
}

// ── API 调用 ──────────────────────────────────────────

export async function askQuestion(question: string, dbName?: string, fmt = 'table'): Promise<AskResponse> {
  const { data } = await api.post('/ask', { question, db_name: dbName, fmt })
  return data
}

export async function getStatus(): Promise<StatusResponse> {
  const { data } = await api.get('/status')
  return data
}

export async function updateLLMConfig(params: {
  base_url?: string
  api_key?: string
  model?: string
}): Promise<{ status: string; llm: { model: string; base_url: string; api_key: string } }> {
  const { data } = await api.post('/llm/config', params)
  return data
}

export async function trainKnowledge(params: {
  ddl?: string
  documentation?: string
  question?: string
  sql?: string
  db?: string
}): Promise<TrainResult> {
  const { data } = await api.post('/train', params)
  return data
}

export async function autoTrain(): Promise<TrainResult> {
  const { data } = await api.post('/train/auto')
  return data
}

export async function submitFeedback(params: {
  question: string
  sql: string
  feedback_type: 'positive' | 'negative' | 'correction'
  corrected_sql?: string
  db_name?: string
}): Promise<FeedbackResult> {
  const { data } = await api.post('/feedback', params)
  return data
}

export async function createConversation(dbName = '', dbType = ''): Promise<ConversationInfo> {
  const { data } = await api.post('/conversation', { db_name: dbName, db_type: dbType })
  return data
}

export async function getConversation(convId: string): Promise<ConversationDetail> {
  const { data } = await api.get(`/conversation/${convId}`)
  return data
}

export async function healthCheck(): Promise<{ status: string }> {
  const { data } = await api.get('/health')
  return data
}

// ── 知识库管理 ─────────────────────────────────────────

export async function clearKnowledge(type: string): Promise<{ status: string; deleted: number }> {
  const { data } = await api.delete(`/knowledge/${type}`)
  return data
}

export async function retrainKnowledge(): Promise<{
  status: string
  cleared: number
  train_result: Record<string, { tables: number; columns: number; trained: number; status: string }>
}> {
  const { data } = await api.post('/knowledge/retrain')
  return data
}

// ── 数据库配置 ──────────────────────────────────────────

export interface DBConfigParams {
  name: string
  type: 'sqlite' | 'postgresql' | 'clickhouse'
  host?: string
  port?: number
  username?: string
  password?: string
  database?: string
  schema?: string
  readonly?: boolean
  max_rows?: number
  timeout?: number
  secure?: boolean
  ssl_mode?: string
}

export interface DBConfigResult {
  status: string
  db_name: string
  connected: boolean
  tables_count: number
  error: string | null
}

export async function testDatabaseConfig(params: DBConfigParams): Promise<DBConfigResult> {
  const { data } = await api.post('/db/test', params)
  return data
}

export async function addDatabaseConfig(params: DBConfigParams): Promise<DBConfigResult> {
  const { data } = await api.post('/db/config', params)
  return data
}

export async function removeDatabaseConfig(name: string): Promise<{ status: string; removed: string }> {
  const { data } = await api.delete(`/db/config/${name}`)
  return data
}

// ── 表信息 ──────────────────────────────────────────────

export interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
  is_primary_key: boolean
}

export interface TableInfo {
  name: string
  comment: string
  columns: ColumnInfo[]
  column_count: number
}

export async function getTables(dbName?: string): Promise<{ db_name: string; tables: TableInfo[] }> {
  const { data } = await api.get('/tables', { params: { db_name: dbName } })
  return data
}

export async function getTableSample(tableName: string, dbName?: string, n = 5): Promise<{
  table: string
  rows: Record<string, unknown>[]
  columns: string[]
}> {
  const { data } = await api.get(`/tables/${tableName}/sample`, { params: { db_name: dbName, n } })
  return data
}
