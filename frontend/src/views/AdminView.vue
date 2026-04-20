<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { getOutfitDetail } from '@/api/outfit'
import { 
  User, 
  Management, 
  Delete, 
  Monitor, 
  Warning, 
  Picture, 
  Setting,
  SwitchButton,
  Shield,
  Check,
  Close,
  View,
  TrendCharts,
  Cpu,
  Moon,
  Sunny,
  Aim,
  Connection,
  Operation
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('dashboard')
const isDark = ref(false)

const toggleDark = () => {
  isDark.value = !isDark.value
}

// 统计数据
const userTotal = ref(0)
const outfitTotal = ref(0)
const reportToday = ref(0)
const aiCallTotal = ref(0)

// 列表数据
const users = ref([])
const userPage = ref(1)
const userTotalCount = ref(0)

const outfits = ref([])
const outfitPage = ref(1)
const outfitTotalCount = ref(0)

const reports = ref([])
const reportPage = ref(1)
const reportTotalCount = ref(0)

// 预览抽屉
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewData = ref<any>(null)
const currentReportId = ref<number | null>(null)

// ECharts 实例
let trendChart: echarts.ECharts | null = null
let aiChartInstance: echarts.ECharts | null = null

const loadUsers = async () => {
  try {
    const res: any = await request.get(`/admin/users?page=${userPage.value}&size=10`)
    users.value = res.records
    userTotalCount.value = res.total
    userTotal.value = res.total // 用于 DashBoard 展示
  } catch (e) {}
}

const loadOutfits = async () => {
  try {
    const res: any = await request.get(`/admin/outfits?page=${outfitPage.value}&size=10`)
    outfits.value = res.records
    outfitTotalCount.value = res.total
    outfitTotal.value = res.total // 用于 DashBoard 展示
  } catch (e) {}
}

const loadReports = async () => {
  try {
    const res: any = await request.get(`/admin/reports?page=${reportPage.value}&size=10`)
    reports.value = res.records
    reportTotalCount.value = res.total
  } catch (e) {}
}

const deleteUser = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？此操作不可逆', '安全警告', { type: 'warning' })
    await request.delete(`/admin/user/${id}`)
    ElMessage.success('用户已删除')
    loadUsers()
  } catch (e) {}
}

const deleteOutfit = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除此贴吗？', '操作确认', { type: 'warning' })
    await request.delete(`/admin/outfit/${id}`)
    ElMessage.success('帖子已删除')
    loadOutfits()
  } catch (e) {}
}

const openPreview = async (row: any) => {
  currentReportId.value = row.id
  previewVisible.value = true
  previewLoading.value = true
  previewData.value = null
  
  try {
    if (row.targetType === 'OUTFIT') {
      const res: any = await getOutfitDetail(row.targetId)
      previewData.value = res
    } else {
      previewData.value = { title: '暂不支持此类型的深度预览', description: `目标类型: ${row.targetType}, ID: ${row.targetId}` }
    }
  } catch (e) {
    ElMessage.error('无法加载违规内容详情')
  } finally {
    previewLoading.value = false
  }
}

const resolveReport = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认为违规并执行封禁处理吗？', '风控决策', { 
      confirmButtonText: '执行封禁',
      type: 'error' 
    })
    await request.post(`/admin/reports/${id}/resolve`)
    ElMessage.success('处理成功')
    previewVisible.value = false
    loadReports()
  } catch (e) {}
}

const rejectReport = async (id: number) => {
  try {
    await request.post(`/admin/reports/${id}/reject`)
    ElMessage.success('已驳回举报')
    previewVisible.value = false
    loadReports()
  } catch (e) {}
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定退出管理系统吗？', '提示', { type: 'info' })
    userStore.clearUser()
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch (e) {}
}

const initDashboard = async () => {
  try {
    const summaryRes: any = await request.get('/admin/stats/summary')
    userTotal.value = summaryRes.userTotal || 0
    outfitTotal.value = summaryRes.outfitTotal || 0

    const res: any = await request.get('/admin/stats/trend?days=7')
    const stats = [...res].reverse() // 按时间正序
    const dates = stats.map(s => s.statDate)
    
    // 更新概览卡片 (取最新一天的数据作为“今日概览”，总数来源于 API 实时加载)
    const latest = stats[stats.length - 1] || {}
    reportToday.value = latest.reportCount || 0
    aiCallTotal.value = stats.reduce((acc, curr) => acc + (curr.aiCallCount || 0), 0)

    await nextTick()

    const trendDom = document.getElementById('chartTrend')
    if (trendDom) {
      if (trendChart) trendChart.dispose()
      trendChart = echarts.init(trendDom)
      trendChart.setOption({
        tooltip: { trigger: 'axis', backgroundColor: isDark.value ? '#1e293b' : '#fff', textStyle: { color: isDark.value ? '#fff' : '#333' } },
        legend: { data: ['用户', '穿搭'], textStyle: { color: isDark.value ? '#cbd5e1' : '#64748b' } },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: dates, axisLabel: { color: isDark.value ? '#64748b' : '#94a3b8' } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: isDark.value ? '#334155' : '#f1f5f9' } } },
        series: [
          { name: '用户', type: 'line', smooth: true, data: stats.map(s => s.newUserCount), color: '#6366f1', areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(99, 102, 241, 0.3)' }, { offset: 1, color: 'rgba(99, 102, 241, 0)' }]) } },
          { name: '穿搭', type: 'line', smooth: true, data: stats.map(s => s.newOutfitCount), color: '#10b981', areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(16, 185, 129, 0.3)' }, { offset: 1, color: 'rgba(16, 185, 129, 0)' }]) } }
        ]
      })
    }

    const aiDom = document.getElementById('chartAi')
    if (aiDom) {
      if (aiChartInstance) aiChartInstance.dispose()
      aiChartInstance = echarts.init(aiDom)
      aiChartInstance.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', data: dates },
        yAxis: { type: 'value' },
        series: [
          { name: 'AI 调用量', type: 'bar', data: stats.map(s => s.aiCallCount), color: '#8b5cf6', barWidth: '60%' }
        ]
      })
    }
  } catch (e) {
    console.error('Stats load failed:', e)
  }
}

const handleResize = () => {
  trendChart?.resize()
  aiChartInstance?.resize()
}

const handleMenuSelect = (index: string) => {
  activeMenu.value = index
  if (index === 'dashboard') {
    initDashboard()
  } else if (index === 'reports') {
    loadReports()
  } else if (index === 'users') {
    loadUsers()
  } else if (index === 'outfits') {
    loadOutfits()
  }
}

onMounted(() => {
  handleMenuSelect('dashboard')
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  aiChartInstance?.dispose()
})
</script>

<template>
  <el-container class="h-screen overflow-hidden transition-all duration-500" :class="{ 'dark': isDark }">
    <!-- 侧边菜单 -->
    <el-aside width="260px" class="admin-sidebar">
      <div class="px-7 py-8 mb-4">
        <div class="flex items-center gap-4 group cursor-pointer">
          <div class="w-11 h-11 rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white shadow-lg shadow-indigo-500/30 group-hover:rotate-12 transition-transform">
            <el-icon size="24"><Shield /></el-icon>
          </div>
          <div>
            <h2 class="font-black tracking-tight text-xl dark:text-white">Admin Hub</h2>
            <p class="text-[9px] text-[#6366f1] uppercase tracking-[0.2em] font-black">Campus Moderation</p>
          </div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="admin-menu flex-1"
        @select="handleMenuSelect"
      >
        <el-menu-item index="dashboard">
          <el-icon><Monitor /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="reports">
          <el-icon><Warning /></el-icon>
          <span>风控工单</span>
          <el-badge v-if="reportToday > 0" :value="reportToday" class="ml-auto" />
        </el-menu-item>
        <el-menu-item index="users">
          <el-icon><User /></el-icon>
          <span>账号资产</span>
        </el-menu-item>
        <el-menu-item index="outfits">
          <el-icon><Picture /></el-icon>
          <span>内容审查</span>
        </el-menu-item>
        <el-menu-item index="settings">
          <el-icon><Setting /></el-icon>
          <span>系统核心</span>
        </el-menu-item>
      </el-menu>

      <div class="p-6">
        <div class="sidebar-user-card">
          <el-avatar :size="36" :src="userStore.userInfo.avatar" class="ring-2 ring-indigo-500/20" />
          <div class="flex-1 min-w-0">
            <p class="text-sm font-bold truncate dark:text-white">{{ userStore.userInfo.username }}</p>
            <p class="text-[10px] text-slate-400 font-bold uppercase truncate">Primary Admin</p>
          </div>
        </div>
      </div>
    </el-aside>

    <el-container class="flex flex-col bg-[#f8fafc] dark:bg-[#0f172a]">
      <!-- 顶部 Header -->
      <el-header height="80px" class="admin-header">
        <div class="flex flex-col justify-center">
          <h3 class="text-xl font-black text-slate-800 dark:text-white">
            {{ 
              activeMenu === 'dashboard' ? 'Overview' : 
              activeMenu === 'reports' ? 'Moderation Queue' : 
              activeMenu === 'users' ? 'Account Assets' : 
              activeMenu === 'outfits' ? 'Content Quality' : 'System Engine'
            }}
          </h3>
          <p class="text-xs text-slate-400 font-medium">Hello, Super Admin. Welcome back.</p>
        </div>
        <div class="flex items-center gap-5">
          <div class="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-full">
            <button 
              class="w-10 h-10 rounded-full flex items-center justify-center transition-all"
              :class="!isDark ? 'bg-white text-orange-500 shadow-sm' : 'text-slate-400 hover:text-white'"
              @click="isDark = false"
            >
              <el-icon :size="18"><Sunny /></el-icon>
            </button>
            <button 
              class="w-10 h-10 rounded-full flex items-center justify-center transition-all"
              :class="isDark ? 'bg-slate-700 text-indigo-400 shadow-sm' : 'text-slate-400 hover:text-slate-900'"
              @click="isDark = true"
            >
              <el-icon :size="18"><Moon /></el-icon>
            </button>
          </div>
          <el-divider direction="vertical" class="!h-8" />
          <el-button link class="admin-logout-btn" @click="handleLogout">
            <el-icon class="mr-2" :size="18"><SwitchButton /></el-icon>安全退出
          </el-button>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="p-8 overflow-y-auto bg-slate-50/50">
        
        <!-- Dashboard 仪表盘 -->
        <div v-if="activeMenu === 'dashboard'" class="max-w-7xl mx-auto space-y-8 animate-in fade-in slide-in-from-bottom-6 duration-700">
          <!-- Bento Grid Stats -->
          <div class="grid grid-cols-4 gap-6">
            <div class="bento-stat bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 col-span-1">
              <div class="flex flex-col h-full justify-between">
                <el-icon :size="32"><User /></el-icon>
                <div>
                  <h4 class="text-3xl font-black mb-1 mt-6 tracking-tight">{{ userTotal }}</h4>
                  <p class="text-[10px] font-black uppercase tracking-widest opacity-60">Total Campus Users</p>
                </div>
              </div>
            </div>
            <div class="bento-stat bg-rose-50 dark:bg-rose-900/20 text-rose-600 dark:text-rose-400 col-span-1">
              <div class="flex flex-col h-full justify-between">
                <el-icon :size="32"><Aim /></el-icon>
                <div>
                  <h4 class="text-3xl font-black mb-1 mt-6 tracking-tight">{{ reportToday }}</h4>
                  <p class="text-[10px] font-black uppercase tracking-widest opacity-60">Critical Reports Today</p>
                </div>
              </div>
              <div class="absolute -right-4 -top-4 opacity-5 rotate-12">
                <el-icon :size="100"><Warning /></el-icon>
              </div>
            </div>
            <div class="bento-stat flex-row items-center gap-6 bg-slate-900 dark:bg-slate-800 text-white col-span-2">
              <div class="flex-1">
                <h4 class="text-4xl font-black tracking-tight mb-2">{{ outfitTotal }}</h4>
                <p class="text-xs text-white/50 font-bold max-w-[180px]">Total content posted and shared across the campus community.</p>
              </div>
              <div class="w-24 h-24 rounded-3xl bg-white/10 flex items-center justify-center backdrop-blur-md">
                <el-icon :size="48"><Picture /></el-icon>
              </div>
            </div>
            <div class="bento-stat bg-white dark:bg-slate-800 col-span-4 overflow-hidden !p-0">
               <div class="flex items-center px-10 h-24 gap-12 border-b border-slate-100 dark:border-slate-700/50">
                  <div class="flex items-center gap-4">
                    <div class="w-10 h-10 rounded-full bg-violet-50 dark:bg-violet-900/30 text-violet-500 flex items-center justify-center">
                      <el-icon :size="20"><Cpu /></el-icon>
                    </div>
                    <div>
                      <p class="text-[10px] font-black text-slate-400 uppercase">AI Total Usage</p>
                      <p class="text-xl font-black dark:text-white">{{ aiCallTotal }}</p>
                    </div>
                  </div>
                  <el-divider direction="vertical" class="!h-10 opacity-30" />
                  <div class="flex-1 flex gap-2 overflow-hidden items-end h-8">
                     <div v-for="i in 20" :key="i" class="flex-1 bg-violet-100 dark:bg-violet-900/20 rounded-t-sm" :style="{ height: Math.random()*100 + '%' }"></div>
                  </div>
               </div>
            </div>
          </div>

          <!-- 图表区域 -->
          <el-row :gutter="24">
            <el-col :span="16">
              <el-card shadow="never" class="pro-card h-[460px]">
                <template #header>
                  <div class="flex items-center justify-between">
                    <span class="font-black text-slate-800 dark:text-white flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-900/30 text-indigo-500 flex items-center justify-center">
                        <el-icon><TrendCharts /></el-icon>
                      </div>
                      业务增长曲线
                    </span>
                    <el-tag size="small" type="primary" effect="plain" class="!border-indigo-100 !bg-indigo-50/50 !rounded-full">实时 · 七天</el-tag>
                  </div>
                </template>
                <div id="chartTrend" class="w-full h-[340px]"></div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never" class="pro-card h-[460px]">
                <template #header>
                  <span class="font-black text-slate-800 dark:text-white flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-violet-50 dark:bg-violet-900/30 text-violet-500 flex items-center justify-center">
                      <el-icon><Monitor /></el-icon>
                    </div>
                    AI 调用分布
                  </span>
                </template>
                <div id="chartAi" class="w-full h-[340px]"></div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <!-- 其他视图保持不变，但在 glass-card 样式上统一 -->
        <div v-else class="max-w-7xl mx-auto animate-in fade-in duration-500">
          
          <!-- Reports 视图 -->
          <div v-if="activeMenu === 'reports'" class="glass-card p-6">
            <el-table :data="reports" border style="width: 100%" v-loading="false">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column label="目标类型" width="100">
                <template #default="scope">
                  <el-tag size="small" :type="scope.row.targetType === 'OUTFIT' ? 'success' : 'warning'">
                    {{ scope.row.targetType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="targetId" label="目标ID" width="100" />
              <el-table-column prop="reason" label="举报原因" min-width="150" show-overflow-tooltip />
              <el-table-column prop="createTime" label="举报时间" width="180" />
              <el-table-column label="操作" width="180" align="center">
                <template #default="scope">
                  <el-button type="primary" size="small" :icon="View" circle @click="openPreview(scope.row)" />
                  <el-divider direction="vertical" />
                  <el-button type="danger" size="small" :icon="Check" circle @click="resolveReport(scope.row.id)" />
                  <el-button type="info" size="small" :icon="Close" circle @click="rejectReport(scope.row.id)" />
                </template>
              </el-table-column>
            </el-table>
            <div class="mt-8 flex justify-end">
              <el-pagination background layout="total, prev, pager, next" :total="reportTotalCount" @current-change="(p) => { reportPage = p; loadReports() }" />
            </div>
          </div>

          <!-- Users 视图 -->
          <div v-if="activeMenu === 'users'" class="glass-card p-6">
            <el-table :data="users" border style="width: 100%">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column label="用户">
                <template #default="scope">
                  <div class="flex items-center gap-3">
                    <el-avatar :size="32" :src="scope.row.avatar" />
                    <span class="font-bold text-slate-700">{{ scope.row.username }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="email" label="邮箱" />
              <el-table-column label="操作" width="120" align="center">
                <template #default="scope">
                  <el-button type="danger" :icon="Delete" plain @click="deleteUser(scope.row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="mt-8 flex justify-end">
              <el-pagination background layout="total, prev, pager, next" :total="userTotalCount" @current-change="(p) => { userPage = p; loadUsers() }" />
            </div>
          </div>

          <!-- Outfits 视图 -->
          <div v-if="activeMenu === 'outfits'" class="glass-card p-6">
            <el-table :data="outfits" border style="width: 100%">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="title" label="标题" min-width="200" />
              <el-table-column label="状态" width="120">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'" effect="dark">{{ scope.row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="center">
                <template #default="scope">
                  <el-button type="danger" :icon="Delete" plain @click="deleteOutfit(scope.row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="mt-8 flex justify-end">
              <el-pagination background layout="total, prev, pager, next" :total="outfitTotalCount" @current-change="(p) => { outfitPage = p; loadOutfits() }" />
            </div>
          </div>

          <!-- Settings 视图 (新版引擎概览) -->
          <div v-if="activeMenu === 'settings'" class="space-y-6">
            <div class="grid grid-cols-3 gap-6">
              <div v-for="node in [
                { title: 'Core API Server', status: 'Healthy', icon: Cpu, load: '12%', color: 'text-emerald-500' },
                { title: 'AI Model Service', status: 'Warm', icon: Connection, load: '45%', color: 'text-orange-500' },
                { title: 'MySQL Primary', status: 'Stable', icon: Operation, load: '8%', color: 'text-blue-500' }
              ]" :key="node.title" class="pro-card p-6 flex flex-col gap-4">
                <div class="flex justify-between items-start">
                  <div class="w-12 h-12 rounded-2xl bg-secondary flex-center">
                    <el-icon :size="24" :class="node.color"><component :is="node.icon" /></el-icon>
                  </div>
                  <el-tag size="small" :type="node.status === 'Healthy' ? 'success' : node.status === 'Warm' ? 'warning' : ''" effect="plain">{{ node.status }}</el-tag>
                </div>
                <div>
                  <h5 class="font-black text-slate-800 dark:text-white">{{ node.title }}</h5>
                  <div class="flex items-center gap-3 mt-2">
                     <el-progress :percentage="parseInt(node.load)" :show-text="false" class="flex-1" :color="node.color.split('-')[1]" />
                     <span class="text-[10px] font-black opacity-40">{{ node.load }}</span>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="pro-card p-10 text-center border-dashed border-2 flex flex-col items-center">
               <div class="w-20 h-20 rounded-full bg-slate-50 dark:bg-slate-800/50 flex-center mb-4">
                  <el-icon size="32" class="text-slate-300"><Setting /></el-icon>
               </div>
               <h4 class="text-xl font-black text-slate-800 dark:text-white mb-2">底层系统配置处于只读模式</h4>
               <p class="text-sm text-slate-400 max-w-md mx-auto">为了确保校园社区的绝对安全，当前的系统层级参数（如：审核敏感词库、存储桶策略）仅限 Root 用户通过 CLI 修改。当前正在开发 UI 授权管理模块。</p>
            </div>
          </div>

        </div>

      </el-main>
    </el-container>

    <!-- 预览抽屉 -->
    <el-drawer v-model="previewVisible" title="违规内容审查" size="50%" destroy-on-close>
      <div v-loading="previewLoading" class="p-4">
        <div v-if="previewData" class="space-y-6">
          <div class="bg-white p-6 rounded-3xl border border-slate-100 shadow-sm">
            <h4 class="text-xl font-black mb-3 text-slate-800">{{ previewData.title }}</h4>
            <p class="text-slate-500 leading-relaxed">{{ previewData.description }}</p>
          </div>

          <div v-if="previewData.imageUrls && previewData.imageUrls.length" class="grid grid-cols-2 gap-4">
            <el-image
              v-for="(url, index) in previewData.imageUrls"
              :key="index"
              :src="url"
              :preview-src-list="previewData.imageUrls"
              fit="cover"
              class="w-full h-72 rounded-3xl shadow-sm border border-white hover:scale-[1.02] transition-transform duration-300"
            />
          </div>

          <div class="flex items-center gap-4 bg-rose-50 p-5 rounded-2xl text-rose-700 text-sm border border-rose-100">
            <el-icon size="20"><Warning /></el-icon>
            <div class="flex-1">
              <span class="font-black block uppercase text-[10px] opacity-40 mb-1">举报由头</span>
              <p class="font-bold">{{ reports.find(r => r.id === currentReportId)?.reason }}</p>
            </div>
          </div>
          
          <div class="flex justify-between items-center text-[10px] text-slate-300 font-bold uppercase tracking-widest px-2">
            <span>Posted: {{ previewData.createTime }}</span>
            <span>Status: {{ previewData.status }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="flex gap-4 p-4">
          <el-button type="danger" shadow class="!h-14 !rounded-2xl flex-1 font-bold text-lg" @click="resolveReport(currentReportId!)">
            证实违规·立即切断
          </el-button>
          <el-button type="info" shadow class="!h-14 !rounded-2xl flex-1 font-bold" @click="rejectReport(currentReportId!)">
            误报·保留内容
          </el-button>
        </div>
      </template>
    </el-drawer>
  </el-container>
</template>

<style scoped>
.admin-sidebar {
  @apply bg-[#0f172a] dark:bg-[#020617] text-white flex flex-col border-r border-white/5 shadow-2xl transition-all duration-500 z-20;
}

.admin-menu {
  @apply !border-none !bg-transparent px-3 overflow-y-auto;
}

:deep(.admin-menu .el-menu-item) {
  @apply h-[52px] rounded-xl mb-1 text-slate-400 font-bold transition-all duration-300;
}

:deep(.admin-menu .el-menu-item:hover) {
  @apply bg-white/5 text-white;
}

:deep(.admin-menu .el-menu-item.is-active) {
  @apply bg-[#6366f1] text-white shadow-lg shadow-indigo-500/20 translate-x-1;
}

.sidebar-user-card {
  @apply flex items-center gap-3 p-4 rounded-[20px] bg-white/5 border border-white/5 backdrop-blur-sm;
}

.admin-header {
  @apply bg-white/80 dark:bg-[#0f172a]/80 backdrop-blur-xl border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-10 shadow-sm z-10 sticky top-0 transition-all duration-500;
}

.admin-logout-btn {
  @apply !text-slate-400 hover:!text-rose-500 transition-colors font-bold text-sm;
}

.bento-stat {
  @apply relative p-8 rounded-[40px] flex flex-col transition-all duration-500 hover:-translate-y-2 hover:shadow-2xl overflow-hidden;
}

.pro-card {
  @apply !bg-white dark:!bg-[#1e293b] !border-none !rounded-[40px] shadow-sm hover:shadow-xl transition-all duration-500;
}

.glass-card {
  @apply bg-white dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-[40px] shadow-sm transition-all duration-500 overflow-hidden;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  @apply !bg-transparent dark:!text-slate-300;
}
.dark :deep(.el-table) {
  --el-table-header-bg-color: #0f172a;
  --el-table-tr-bg-color: transparent;
  --el-table-border-color: #334155;
  --el-table-row-hover-bg-color: #334155;
}

:deep(.el-card__header) {
  @apply border-b border-slate-50 dark:border-slate-700/50 px-10 py-6;
}
:deep(.el-card__body) {
  @apply px-10 py-8;
}

.flex-center {
  @apply flex items-center justify-center;
}

/* Animations */
@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-slide-up {
  animation: slide-up 0.6s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
</style>
