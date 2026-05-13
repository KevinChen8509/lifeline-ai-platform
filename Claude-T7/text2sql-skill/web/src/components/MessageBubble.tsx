import { Chip } from '@heroui/react'
import type { AskResponse, TableInfo } from '../api/client'
import SqlPanel from './SqlPanel'
import ResultTable from './ResultTable'
import ChartView from './ChartView'
import SchemaView from './SchemaView'

interface Message {
  role: 'user' | 'assistant'
  content: string
  result?: AskResponse
  tables?: TableInfo[]
  sampleData?: { table: string; rows: Record<string, unknown>[]; columns: string[] }
  cancelled?: boolean
}

export default function MessageBubble({ message }: { message: Message }) {
  const isUser = message.role === 'user'

  if (message.cancelled) {
    return (
      <div className="flex justify-start">
        <div className="bg-gray-50 rounded-lg px-4 py-2 text-sm text-gray-400 italic">
          {message.content}
        </div>
      </div>
    )
  }

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="bg-indigo-600 text-white rounded-lg px-4 py-2 max-w-lg">
          <p className="text-sm">{message.content}</p>
        </div>
      </div>
    )
  }

  const r = message.result
  const hasError = r?.error

  return (
    <div className="flex justify-start">
      <div className="bg-white rounded-lg shadow-sm border border-gray-100 max-w-4xl w-full overflow-hidden">
        {/* 状态栏 */}
        <div className="flex items-center gap-2 px-4 py-2 border-b border-gray-100 bg-gray-50">
          {r && (
            <>
              {hasError ? (
                <Chip size="sm" color="danger" variant="soft">失败</Chip>
              ) : (
                <Chip size="sm" color="success" variant="soft">成功</Chip>
              )}
              {r.autofixed && (
                <Chip size="sm" color="warning" variant="soft">
                  自动修复 ({r.autofix_attempts}次)
                </Chip>
              )}
              <span className="text-xs text-gray-400 ml-auto">
                {r.elapsed_ms.toFixed(0)}ms | {r.row_count}行
              </span>
            </>
          )}
          {message.tables && (
            <Chip size="sm" color="accent" variant="soft">表信息</Chip>
          )}
          {message.sampleData && (
            <Chip size="sm" color="accent" variant="soft">样本数据</Chip>
          )}
        </div>

        {/* 表信息 */}
        {message.tables && <SchemaView tables={message.tables} />}

        {/* 样本数据 */}
        {message.sampleData && message.sampleData.rows.length > 0 && (
          <div className="px-4 py-3">
            <p className="text-xs text-gray-400 mb-2">
              {message.sampleData.table} 表样本数据 ({message.sampleData.rows.length}行)
            </p>
            <ResultTable data={message.sampleData.rows} />
          </div>
        )}

        {/* SQL 面板 */}
        {r?.sql && <SqlPanel sql={r.sql} />}

        {/* 错误信息 */}
        {hasError && (
          <div className="px-4 py-3 text-sm text-red-600">
            {r.error}
          </div>
        )}

        {/* 数据表格 */}
        {r?.data && r.data.length > 0 && <ResultTable data={r.data} />}

        {/* 图表 — 仅在有实际图表推荐时显示 */}
        {r?.chart_recommendation && r.chart_recommendation.chart_type !== 'table' && r.data && r.data.length > 0 && (
          <ChartView data={r.data} chart={r.chart_recommendation} />
        )}

        {/* 纯文本内容 */}
        {!r && !message.tables && !message.sampleData && (
          <div className="px-4 py-3 text-sm text-gray-600">{message.content}</div>
        )}
      </div>
    </div>
  )
}
