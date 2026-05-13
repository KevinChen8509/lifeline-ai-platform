import { useState } from 'react'
import { Button, Chip } from '@heroui/react'
import Plot from 'react-plotly.js'

interface Props {
  data: Record<string, unknown>[]
  chart: {
    chart_type: string
    x_column: string
    y_column: string
    color_column: string
    reason: string
  }
}

export default function ChartView({ data, chart }: Props) {
  const [visible, setVisible] = useState(false)

  // 安全检查：列名必须在数据中存在
  if (!chart.x_column && !chart.y_column) return null
  const hasX = chart.x_column && data.length > 0 && chart.x_column in data[0]
  const hasY = chart.y_column && data.length > 0 && chart.y_column in data[0]
  if (!hasX && !hasY) return null

  const xVals = hasX ? data.map((r) => r[chart.x_column!]) : data.map((_, i) => i + 1)
  const yVals = hasY ? data.map((r) => Number(r[chart.y_column!]) || 0) : []

  if (!visible) {
    return (
      <div className="px-4 py-2 border-t border-gray-100">
        <div className="flex items-center gap-2">
          <Chip size="sm" variant="soft" color="accent">
            {chart.chart_type.toUpperCase()}
          </Chip>
          <span className="text-xs text-gray-400">{chart.reason}</span>
          <Button
            variant="ghost"
            className="text-xs ml-auto"
            onPress={() => setVisible(true)}
          >
            显示图表
          </Button>
        </div>
      </div>
    )
  }

  const isPie = chart.chart_type === 'pie'
  const isHistogram = chart.chart_type === 'histogram'

  return (
    <div className="px-4 py-3 border-t border-gray-100">
      <div className="flex items-center gap-2 mb-2">
        <Chip size="sm" variant="soft" color="accent">
          {chart.chart_type.toUpperCase()}
        </Chip>
        <span className="text-xs text-gray-400">{chart.reason}</span>
        <Button
          variant="ghost"
          className="text-xs ml-auto"
          onPress={() => setVisible(false)}
        >
          收起
        </Button>
      </div>
      <Plot
        data={[
          isPie
            ? { type: 'pie' as const, labels: xVals as string[], values: yVals }
            : isHistogram
              ? { type: 'histogram' as any, x: yVals }
              : {
                  type: (chart.chart_type === 'scatter' ? 'scatter' : 'bar') as any,
                  x: xVals,
                  y: yVals,
                  marker: { color: '#6366f1' },
                },
        ]}
        layout={{
          margin: { t: 20, r: 20, b: 40, l: 50 },
          xaxis: { title: chart.x_column },
          yaxis: { title: chart.y_column },
          paper_bgcolor: 'transparent',
          plot_bgcolor: 'transparent',
          font: { size: 11 },
        }}
        config={{ displayModeBar: false, responsive: true }}
        style={{ width: '100%', height: '320px' }}
      />
    </div>
  )
}
