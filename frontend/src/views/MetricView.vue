<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '../api'

const form = ref({
  metric_name: '',
  synonyms: '',
  expression: '',
  related_tables: 'orders',
  definition: '',
  template_sql: ''
})
const metricId = ref('')
const message = ref('')
const messageType = ref<'success' | 'error'>('success')

type MetricRow = {
  metric_id: number
  metric_name: string
  synonyms: string
  expression: string
  related_tables: string
  definition: string
  template_sql: string
  version: number
  status: 'draft' | 'published' | 'deprecated'
  approved_by: number | null
  approved_at: string | null
  created_by: number | null
}

const metrics = ref<MetricRow[]>([])
const loading = ref(false)

function toast(msg: string, type: 'success' | 'error' = 'success') {
  message.value = msg
  messageType.value = type
}

async function loadMetrics() {
  loading.value = true
  try {
    const resp = await api.get('/metrics')
    metrics.value = resp.data.data ?? []
  } catch (e: any) {
    toast(e.response?.data?.message || '加载指标失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadMetrics)

async function create() {
  toast('', 'success')
  try {
    const resp = await api.post('/metrics', form.value)
    toast(`创建成功 metric_id=${resp.data.data.metric_id}，状态=${resp.data.data.status}`)
    form.value = { metric_name: '', synonyms: '', expression: '', related_tables: 'orders', definition: '', template_sql: '' }
    await loadMetrics()
  } catch (e: any) {
    toast(e.response?.data?.message || '创建失败', 'error')
  }
}

async function approve(id: string | number) {
  toast('', 'success')
  try {
    const resp = await api.post(`/metrics/${id}/approve`)
    toast(`审批成功 状态=${resp.data.data.status}`)
    await loadMetrics()
  } catch (e: any) {
    toast(e.response?.data?.message || '审批失败', 'error')
  }
}

async function deprecate(id: string | number) {
  toast('', 'success')
  try {
    const resp = await api.post(`/metrics/${id}/deprecate`)
    toast(`废弃成功 状态=${resp.data.data.status}`)
    await loadMetrics()
  } catch (e: any) {
    toast(e.response?.data?.message || '废弃失败', 'error')
  }
}

const statusColor: Record<MetricRow['status'], string> = {
  draft: '#faad14',
  published: '#52c41a',
  deprecated: '#999'
}
const statusLabel: Record<MetricRow['status'], string> = {
  draft: '草稿',
  published: '已发布',
  deprecated: '已废弃'
}
</script>

<template>
  <div class="metric-page">
    <h3>指标管理</h3>

    <section class="card">
      <h4>新建指标</h4>
      <div class="form">
        <input v-model="form.metric_name" placeholder="指标名 *" />
        <input v-model="form.synonyms" placeholder="同义词（英文逗号分隔，如 单均件数,平均件数）" />
        <input v-model="form.expression" placeholder="表达式 *，如 SUM(quantity)/COUNT(DISTINCT order_id)" />
        <input v-model="form.related_tables" placeholder="关联表 *，如 orders" />
        <input v-model="form.definition" placeholder="口径说明 *，如 平均每单的商品件数（去重订单）" />
        <input v-model="form.template_sql" placeholder="模板 SQL *，如 SELECT ... FROM orders" />
        <button class="primary" @click="create">创建指标</button>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <h4>指标列表</h4>
        <button class="ghost" @click="loadMetrics" :disabled="loading">
          {{ loading ? '加载中...' : '刷新' }}
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width:72px">ID</th>
              <th style="width:120px">指标名</th>
              <th>同义词</th>
              <th style="width:200px">表达式</th>
              <th style="width:90px">关联表</th>
              <th style="width:72px">版本</th>
              <th style="width:86px">状态</th>
              <th style="width:220px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in metrics" :key="m.metric_id">
              <td class="mono">{{ m.metric_id }}</td>
              <td class="strong">{{ m.metric_name }}</td>
              <td :title="m.synonyms">{{ m.synonyms || '—' }}</td>
              <td class="mono small" :title="m.expression">{{ m.expression }}</td>
              <td>{{ m.related_tables }}</td>
              <td class="mono">v{{ m.version }}</td>
              <td>
                <span class="tag" :style="{ background: statusColor[m.status] }">
                  {{ statusLabel[m.status] }}
                </span>
              </td>
              <td>
                <button
                  class="primary small"
                  :disabled="m.status !== 'draft'"
                  :title="m.status === 'draft' ? '审批：draft → published，允许指标匹配' : '仅草稿可审批'"
                  @click="approve(m.metric_id)">审批</button>
                <button
                  class="danger small"
                  :disabled="m.status !== 'published'"
                  :title="m.status === 'published' ? '废弃：published → deprecated，停止匹配' : '仅已发布可废弃'"
                  @click="deprecate(m.metric_id)">废弃</button>
              </td>
            </tr>
            <tr v-if="metrics.length === 0 && !loading">
              <td colspan="8" class="empty">暂无指标</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="hint">提示：每行按钮按当前状态自动开关。面试时可直接从这里“新建 → 看它出现在表格顶 → 审批 → 变绿 → 问数命中 → 废弃 → 变灰”端到端演示。</p>
    </section>

    <section class="card compact">
      <p class="form-label">快速操作（输入 metric_id 直接操作，兼容旧用法）</p>
      <div class="ops">
        <input v-model="metricId" placeholder="指标 ID" />
        <button @click="approve(metricId)">审批</button>
        <button class="danger" @click="deprecate(metricId)">废弃</button>
      </div>
    </section>

    <p v-if="message" class="msg" :class="messageType">{{ message }}</p>
  </div>
</template>

<style scoped>
.metric-page { max-width: 1180px; margin: 0 auto; padding: 16px; }
h3 { margin: 8px 0 16px; font-size: 20px; }
h4 { margin: 0; font-size: 15px; color: #333; }

.card { background: #fff; border: 1px solid #eef0f3; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 2px rgba(0,0,0,.03); }
.card.compact { padding: 12px 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }

.form { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.form input, .ops input { padding: 10px 12px; border: 1px solid #e4e6eb; border-radius: 6px; outline: none; transition: border-color .15s; }
.form input:focus, .ops input:focus { border-color: #1677ff; }
.form button, .ops button { padding: 10px 16px; border-radius: 6px; cursor: pointer; font-size: 14px; }
.form button { background: #1677ff; color: #fff; border: none; }

button.primary { background: #1677ff; color: #fff; border: 1px solid #1677ff; }
button.primary:disabled { background: #91caff; border-color: #91caff; cursor: not-allowed; }
button.danger { background: #fff; color: #d4380d; border: 1px solid #ffa39e; }
button.danger:disabled { color: #d9d9d9; border-color: #eee; cursor: not-allowed; }
button.ghost { background: #fff; color: #555; border: 1px solid #ddd; padding: 6px 14px; border-radius: 6px; cursor: pointer; }
button.ghost:disabled { opacity: .6; cursor: not-allowed; }
button.small { padding: 5px 12px; font-size: 13px; margin-right: 6px; }

.ops { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
.ops button { background: #fff; border: 1px solid #ddd; }
.ops input { flex: 1; }

.form-label { margin: 0 0 4px; color: #666; font-size: 13px; }

.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f1f3f6; vertical-align: top; }
th { background: #fafbfc; color: #555; font-weight: 600; font-size: 13px; }
tbody tr:hover { background: #f8fbff; }

.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 13px; }
.small { font-size: 12px; word-break: break-all; }
.strong { font-weight: 600; color: #111; }

.tag { display: inline-block; padding: 2px 8px; border-radius: 999px; color: #fff; font-size: 12px; }

.empty { padding: 32px 0; text-align: center; color: #999; }

.msg { margin-top: 12px; padding: 10px 14px; border-radius: 6px; }
.msg.success { background: #eaf7ec; color: #237804; }
.msg.error { background: #fff1f0; color: #cf1322; }

.hint { margin: 12px 0 0; color: #888; font-size: 12px; line-height: 1.6; }
</style>
