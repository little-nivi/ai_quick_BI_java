<script setup lang="ts">
import { ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import api from '../api'

echarts.use([BarChart, TitleComponent, TooltipComponent, GridComponent, CanvasRenderer])

const question = ref('')
const loading = ref(false)
const result = ref<any>(null)
const showTrace = ref(false)
const feedbackSent = ref(false)

const examples = [
  '华南地区上个月的总订单量',
  '每个用户的消费金额排名前10',
  '近7天每天的订单数',
  '各商品类目的销售额占比'
]
function askExample(q: string) {
  question.value = q
  ask()
}
function reset() {
  question.value = ''
  result.value = null
  showTrace.value = false
  feedbackSent.value = false
}

let chart: any = null

function hasDimension(columns: string[], rows: any[][]): boolean {
  // 维度列：非数字的第一列 + 数值列
  if (!columns || columns.length < 2 || !rows || rows.length === 0) return false
  return true
}

function renderChart() {
  if (!result.value) return
  const { columns, rows } = result.value
  if (!hasDimension(columns, rows)) return

  const el = document.getElementById('chart') as HTMLElement
  if (!el) return
  if (chart) chart.dispose()
  chart = echarts.init(el)

  // 取第一列作维度（类别），第二列作数值
  const categories = rows.map((r: any[]) => String(r[0]))
  const values = rows.map((r: any[]) => Number(r[1]))

  chart.setOption({
    title: { text: '查询结果' },
    tooltip: {},
    xAxis: { type: 'category', data: categories },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: values }]
  })
}

async function ask() {
  if (!question.value.trim()) return
  loading.value = true
  result.value = null
  feedbackSent.value = false
  try {
    const resp = await api.post('/query', { question: question.value })
    if (resp.data.code === 2000) {
      result.value = resp.data.data
      await new Promise((r) => setTimeout(r, 50))
      renderChart()
    } else {
      result.value = { error: resp.data.message, clarification: resp.data.data?.clarification }
    }
  } catch (e: any) {
    result.value = { error: e.response?.data?.message || '查询失败' }
  } finally {
    loading.value = false
  }
}

async function feedback(type: 'positive' | 'negative') {
  if (!result.value || !result.value.sql) return
  try {
    await api.post('/feedback', {
      question: question.value,
      generated_sql: result.value.sql,
      feedback_type: type
    })
    feedbackSent.value = true
  } catch (e) {
    console.error('feedback failed', e)
  }
}
</script>

<template>
  <div class="query-page">
    <div class="input-row">
      <input v-model="question" placeholder="输入你的问题，例如：总销售额是多少" @keyup.enter="ask" />
      <button @click="ask" :disabled="loading">{{ loading ? '查询中...' : '问数' }}</button>
    </div>

    <div v-if="!result && !loading" class="examples">
      <span class="examples-label">试试这些问题：</span>
      <button v-for="ex in examples" :key="ex" class="example-chip" @click="askExample(ex)">{{ ex }}</button>
    </div>

    <div v-if="result?.error" class="error-box">
      <p>{{ result.error }}</p>
      <div v-if="result.clarification" class="clarify">
        <p>{{ result.clarification.question }}</p>
        <button v-for="opt in result.clarification.options" :key="opt" @click="question = question + ' ' + opt; ask()">
          {{ opt }}
        </button>
      </div>
    </div>

    <div v-if="result && !result.error" class="result-box">
      <div class="meta">
        <span class="metric">匹配指标：{{ result.matchedMetric || '—' }}</span>
        <span class="metric">置信度：{{ result.confidence ?? '—' }}</span>
        <span v-if="result.cacheHit" class="metric cache">缓存命中</span>
      </div>

      <div v-if="result.rowCount === 1 && result.columns?.length === 1" class="kpi">
        <div class="kpi-value">{{ result.rows?.[0]?.[0] }}</div>
        <div class="kpi-label">{{ result.columns?.[0] }}</div>
      </div>
      <div v-else>
        <div id="chart" class="chart"></div>
        <table class="result-table">
          <thead>
            <tr><th v-for="c in result.columns" :key="c">{{ c }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in result.rows" :key="i">
              <td v-for="(cell, j) in row" :key="j">{{ cell }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="sql-box">生成 SQL：{{ result.sql }}</div>

      <div class="actions">
        <button class="btn-primary" @click="reset">新查询</button>
        <button @click="showTrace = !showTrace">查询依据</button>
        <button v-if="!feedbackSent" @click="feedback('positive')">有用</button>
        <button v-if="!feedbackSent" @click="feedback('negative')">无用</button>
        <span v-if="feedbackSent" class="sent">已反馈</span>
      </div>

      <div v-if="showTrace && result.trace" class="trace">
        <p><strong>表：</strong>{{ result.trace.tables?.join(', ') }}</p>
        <p><strong>字段：</strong>{{ result.trace.fields?.join(', ') }}</p>
        <p><strong>指标：</strong>{{ result.trace.metric || '—' }}</p>
        <p><strong>置信度：</strong>{{ result.trace.confidenceLevel || '—' }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.query-page { max-width: 900px; margin: 0 auto; }
.input-row { display: flex; gap: 12px; }
.input-row input { flex: 1; padding: 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 15px; }
.input-row button { padding: 12px 24px; background: #1677ff; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.examples { margin-top: 16px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.examples-label { font-size: 13px; color: #999; }
.example-chip { padding: 6px 14px; background: #f5f5f5; border: 1px solid #e8e8e8; border-radius: 16px; font-size: 13px; color: #333; cursor: pointer; transition: all 0.2s; }
.example-chip:hover { background: #e6f4ff; border-color: #1677ff; color: #1677ff; }
.error-box { margin-top: 20px; padding: 16px; background: #fff1f0; border-radius: 6px; }
.clarify button { margin: 4px; }
.result-box { margin-top: 20px; }
.meta { display: flex; gap: 16px; margin-bottom: 12px; }
.metric { font-size: 13px; color: #666; }
.cache { color: #52c41a; }
.kpi { text-align: center; padding: 40px; }
.kpi-value { font-size: 48px; font-weight: 700; }
.kpi-label { color: #999; }
.chart { width: 100%; height: 300px; margin-bottom: 16px; }
.result-table { width: 100%; border-collapse: collapse; }
.result-table th, .result-table td { border: 1px solid #eee; padding: 8px 12px; text-align: left; }
.sql-box { margin-top: 12px; padding: 12px; background: #f5f5f5; border-radius: 6px; font-family: monospace; font-size: 13px; }
.actions { margin-top: 12px; display: flex; gap: 8px; }
.actions button { padding: 6px 16px; border: 1px solid #ddd; background: #fff; border-radius: 4px; cursor: pointer; }
.actions .btn-primary { background: #1677ff; color: #fff; border-color: #1677ff; }
.sent { color: #52c41a; font-size: 13px; }
.trace { margin-top: 12px; padding: 12px; background: #fafafa; border-radius: 6px; font-size: 13px; }
</style>
