<script setup lang="ts">
import { ref } from 'vue'
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

async function create() {
  message.value = ''
  try {
    const resp = await api.post('/metrics', form.value)
    message.value = `创建成功 metric_id=${resp.data.data.metric_id}，状态=${resp.data.data.status}`
  } catch (e: any) {
    message.value = e.response?.data?.message || '创建失败'
  }
}

async function approve(id: string) {
  message.value = ''
  try {
    const resp = await api.post(`/metrics/${id}/approve`)
    message.value = `审批成功 状态=${resp.data.data.status}`
  } catch (e: any) {
    message.value = e.response?.data?.message || '审批失败'
  }
}

async function deprecate(id: string) {
  message.value = ''
  try {
    const resp = await api.post(`/metrics/${id}/deprecate`)
    message.value = `废弃成功 状态=${resp.data.data.status}`
  } catch (e: any) {
    message.value = e.response?.data?.message || '废弃失败'
  }
}
</script>

<template>
  <div class="metric-page">
    <h3>指标管理</h3>
    <div class="form">
      <input v-model="form.metric_name" placeholder="指标名" />
      <input v-model="form.synonyms" placeholder="同义词（逗号分隔）" />
      <input v-model="form.expression" placeholder="表达式，如 SUM(amount)" />
      <input v-model="form.related_tables" placeholder="关联表" />
      <input v-model="form.definition" placeholder="口径说明" />
      <input v-model="form.template_sql" placeholder="模板 SQL" />
      <button @click="create">创建指标</button>
    </div>

    <div class="ops">
      <input v-model="metricId" placeholder="指标 ID" />
      <button @click="approve(metricId)">审批</button>
      <button @click="deprecate(metricId)">废弃</button>
    </div>

    <p v-if="message" class="msg">{{ message }}</p>
  </div>
</template>

<style scoped>
.metric-page { max-width: 600px; margin: 0 auto; }
.form { display: flex; flex-direction: column; gap: 10px; margin: 16px 0; }
.form input { padding: 10px; border: 1px solid #ddd; border-radius: 4px; }
.form button { padding: 10px; background: #1677ff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.ops { display: flex; gap: 8px; align-items: center; }
.ops input { flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 4px; }
.ops button { padding: 10px 16px; border: 1px solid #ddd; background: #fff; border-radius: 4px; cursor: pointer; }
.msg { margin-top: 12px; color: #1677ff; }
</style>
