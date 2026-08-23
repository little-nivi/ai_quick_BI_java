/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// echarts 5 子路径无独立类型声明，按需引入时需声明（运行时 tree-shaking 不受影响）
declare module 'echarts/core'
declare module 'echarts/charts'
declare module 'echarts/components'
declare module 'echarts/renderers'
