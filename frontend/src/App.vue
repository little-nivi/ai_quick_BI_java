<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const role = ref('')
const dataScope = ref('')

function refreshUser() {
  role.value = localStorage.getItem('role') || ''
  dataScope.value = localStorage.getItem('data_scope') || ''
}

// 登录跳转后重新读取（App 根组件不重建，需监听路由）
watch(() => route.fullPath, refreshUser, { immediate: true })

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
  localStorage.removeItem('data_scope')
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="brand">NL2SQL 问数系统</div>
      <nav class="nav">
        <router-link to="/">问数</router-link>
        <router-link to="/history">历史</router-link>
        <router-link to="/metrics">指标管理</router-link>
      </nav>
      <div class="user">
        <span class="role-badge">{{ role }}</span>
        <span v-if="dataScope && dataScope !== 'all'" class="scope">范围：{{ dataScope }}</span>
        <button @click="logout">退出</button>
      </div>
    </header>
    <main class="content">
      <slot />
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.header { display: flex; align-items: center; gap: 24px; padding: 12px 20px; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; font-size: 18px; }
.nav { display: flex; gap: 16px; }
.nav a { color: #333; text-decoration: none; }
.nav a.router-link-active { color: #1677ff; }
.user { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.role-badge { background: #1677ff; color: #fff; padding: 2px 10px; border-radius: 12px; font-size: 12px; }
.scope { font-size: 12px; color: #666; }
.content { flex: 1; padding: 20px; }
</style>
