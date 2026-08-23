<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '../api'

const sessions = ref<any[]>([])

onMounted(async () => {
  try {
    const resp = await api.get('/sessions')
    sessions.value = resp.data.data || []
  } catch (e) {
    console.error('load sessions failed', e)
  }
})

function formatTime(t: string) {
  return t ? new Date(t).toLocaleString() : ''
}
</script>

<template>
  <div class="history-page">
    <h3>历史记录</h3>
    <table class="history-table">
      <thead>
        <tr><th>问题</th><th>SQL</th><th>缓存</th><th>时间</th></tr>
      </thead>
      <tbody>
        <tr v-for="(s, i) in sessions" :key="i">
          <td>{{ s.question }}</td>
          <td class="mono">{{ s.generated_sql }}</td>
          <td>{{ s.cache_hit ? '是' : '否' }}</td>
          <td>{{ formatTime(s.created_at) }}</td>
        </tr>
        <tr v-if="sessions.length === 0"><td colspan="4">暂无历史记录</td></tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.history-page { max-width: 900px; margin: 0 auto; }
.history-table { width: 100%; border-collapse: collapse; }
.history-table th, .history-table td { border: 1px solid #eee; padding: 8px 12px; text-align: left; font-size: 13px; }
.mono { font-family: monospace; }
</style>
