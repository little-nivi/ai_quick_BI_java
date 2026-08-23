<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

async function login() {
  error.value = ''
  try {
    const resp = await api.post('/auth/login', { username: username.value, password: password.value })
    const data = resp.data.data
    localStorage.setItem('token', data.token)
    localStorage.setItem('role', data.role)
    localStorage.setItem('data_scope', data.dataScope || data.data_scope || 'all')
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || '登录失败'
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h2>NL2SQL 问数系统</h2>
      <input v-model="username" placeholder="用户名" />
      <input v-model="password" type="password" placeholder="密码" @keyup.enter="login" />
      <button @click="login">登录</button>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="hint">预置账号：admin/admin123 · operator/operator123</div>
    </div>
  </div>
</template>

<style scoped>
.login-page { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #f5f5f5; }
.login-card { background: #fff; padding: 40px; border-radius: 8px; width: 320px; display: flex; flex-direction: column; gap: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.login-card h2 { text-align: center; margin-bottom: 8px; }
input { padding: 10px; border: 1px solid #ddd; border-radius: 4px; }
button { padding: 10px; background: #1677ff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.error { color: #f5222d; font-size: 13px; }
.hint { color: #999; font-size: 12px; text-align: center; }
</style>
