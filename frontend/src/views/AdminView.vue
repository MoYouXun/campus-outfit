<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { getOutfitDetail } from '@/api/outfit'
import { 
  User, 
  HotWater, 
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
  Cpu
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('dashboard')

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
    const res: any = await request.get('/admin/stats/trend?days=30')
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
        tooltip: { trigger: 'axis' },
        legend: { data: ['新增用户', '新增穿搭'] },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: dates },
        yAxis: { type: 'value' },
        series: [
          { name: '新增用户', type: 'line', smooth: true, data: stats.map(s => s.newUserCount), color: '#3b82f6', areaStyle: { opacity: 0.1 } },
          { name: '新增穿搭', type: 'line', smooth: true, data: stats.map(s => s.newOutfitCount), color: '#10b981', areaStyle: { opacity: 0.1 } }
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
  <el-container class="h-screen bg-[#f8fafc] overflow-hidden">
    <!-- 侧边菜单 -->
    <el-aside width="240px" class="bg-[#1e1e2d] text-white flex flex-col border-r border-white/5 shadow-xl overflow-hidden">
      <div class="p-6 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-[#3b82f6] flex items-center justify-center text-white shadow-lg shadow-blue-500/20">
          <el-icon size="20"><Management /></el-icon>
        </div>
        <div>
          <h2 class="font-black tracking-tight text-lg leading-tight">Campus Outfit</h2>
          <p class="text-[10px] text-white/40 uppercase tracking-widest font-bold">Admin Central 2.0</p>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="flex-1 !border-none !bg-transparent overflow-y-auto"
        text-color="rgba(255, 255, 255, 0.7)"
        active-text-color="#ffffff"
        @select="handleMenuSelect"
      >
        <el-menu-item index="dashboard">
          <el-icon><Monitor /></el-icon>
          <span>数据大盘</span>
        </el-menu-item>
        <el-menu-item index="reports">
          <el-icon><Warning /></el-icon>
          <span>风控审核</span>
        </el-menu-item>
        <el-menu-item index="users">
          <el-icon><User /></el-icon>
          <span>账号管理</span>
        </el-menu-item>
        <el-menu-item index="outfits">
          <el-icon><Picture /></el-icon>
          <span>内容管理</span>
        </el-menu-item>
        <el-menu-item index="settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>

      <div class="p-4 border-t border-white/5">
        <div class="flex items-center gap-3 p-3 rounded-xl bg-white/5">
          <el-avatar :size="32" :src="userStore.userInfo.avatar" class="border border-white/10" />
          <div class="flex-1 min-w-0">
            <p class="text-sm font-bold text-white truncate">{{ userStore.userInfo.username || '管理员' }}</p>
            <p class="text-[10px] text-white/40 truncate">System Operator</p>
          </div>
        </div>
      </div>
    </el-aside>

    <el-container class="flex flex-col">
      <!-- 顶部 Header -->
      <el-header height="72px" class="bg-white border-b border-slate-200 flex items-center justify-between px-8 shadow-sm z-10">
        <div>
          <h3 class="text-lg font-bold text-slate-800">
            {{ 
              activeMenu === 'dashboard' ? '仪表盘概览' : 
              activeMenu === 'reports' ? '待处理举报' : 
              activeMenu === 'users' ? '注册用户列表' : 
              activeMenu === 'outfits' ? '内容发布管理' : '全局系统设置'
            }}
          </h3>
        </div>
        <div class="flex items-center gap-4">
          <el-button link class="text-slate-500 hover:text-red-500 transition-colors" @click="handleLogout">
            <el-icon class="mr-1"><SwitchButton /></el-icon>退出登录
          </el-button>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="p-8 overflow-y-auto bg-slate-50/50">
        
        <!-- Dashboard 视图 -->
        <div v-if="activeMenu === 'dashboard'" class="max-w-7xl mx-auto space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <!-- 概览卡片 -->
          <el-row :gutter="24">
            <el-col :span="6">
              <el-card shadow="never" class="!rounded-3xl border-none shadow-sm hover:shadow-md transition-all">
                <div class="flex items-center gap-5">
                  <div class="w-14 h-14 rounded-2xl bg-blue-50 flex items-center justify-center text-blue-600">
                    <el-icon size="24"><User /></el-icon>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400 font-bold uppercase tracking-wider">总用户数</p>
                    <h4 class="text-2xl font-black text-slate-800">{{ userTotal }}</h4>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" class="!rounded-3xl border-none shadow-sm hover:shadow-md transition-all">
                <div class="flex items-center gap-5">
                  <div class="w-14 h-14 rounded-2xl bg-emerald-50 flex items-center justify-center text-emerald-600">
                    <el-icon size="24"><Picture /></el-icon>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400 font-bold uppercase tracking-wider">总帖子数</p>
                    <h4 class="text-2xl font-black text-slate-800">{{ outfitTotal }}</h4>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" class="!rounded-3xl border-none shadow-sm hover:shadow-md transition-all">
                <div class="flex items-center gap-5">
                  <div class="w-14 h-14 rounded-2xl bg-rose-50 flex items-center justify-center text-rose-600">
                    <el-icon size="24"><Warning /></el-icon>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400 font-bold uppercase tracking-wider">今日新增举报</p>
                    <h4 class="text-2xl font-black text-slate-800 text-rose-600">{{ reportToday }}</h4>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" class="!rounded-3xl border-none shadow-sm hover:shadow-md transition-all">
                <div class="flex items-center gap-5">
                  <div class="w-14 h-14 rounded-2xl bg-violet-50 flex items-center justify-center text-violet-600">
                    <el-icon size="24"><Cpu /></el-icon>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400 font-bold uppercase tracking-wider">AI 总调用量</p>
                    <h4 class="text-2xl font-black text-slate-800">{{ aiCallTotal }}</h4>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 图表区域 -->
          <el-row :gutter="24">
            <el-col :span="14">
              <el-card shadow="never" class="!rounded-3xl border-none h-[420px]">
                <template #header>
                  <div class="flex items-center justify-between">
                    <span class="font-bold text-slate-700 flex items-center gap-2">
                      <el-icon class="text-blue-500"><TrendCharts /></el-icon> 活跃度增长趋势
                    </span>
                    <el-tag size="small" type="info">近 30 天</el-tag>
                  </div>
                </template>
                <div id="chartTrend" class="w-full h-[320px]"></div>
              </el-card>
            </el-col>
            <el-col :span="10">
              <el-card shadow="never" class="!rounded-3xl border-none h-[420px]">
                <template #header>
                  <span class="font-bold text-slate-700 flex items-center gap-2">
                    <el-icon class="text-violet-500"><Monitor /></el-icon> AI 接口负载统计
                  </span>
                </template>
                <div id="chartAi" class="w-full h-[320px]"></div>
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

          <!-- Settings 视图 -->
          <div v-if="activeMenu === 'settings'" class="glass-card p-12 text-center text-slate-400">
            <el-icon size="64" class="mb-4 opacity-10"><Setting /></el-icon>
            <p class="text-xl font-bold">系统设置正在开发中</p>
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
.glass-card {
  @apply bg-white border border-slate-200 rounded-[2.5rem] shadow-sm hover:shadow-xl hover:-translate-y-1 transition-all duration-500;
}

:deep(.el-menu-item.is-active) {
  @apply bg-[#3b82f6]/10 font-bold border-r-4 border-[#3b82f6] !text-white;
}

:deep(.el-menu-item) {
  @apply h-14 mx-4 rounded-xl mb-1 !text-white/50 transition-all duration-300;
}

:deep(.el-menu-item:hover) {
  @apply bg-white/5 !text-white;
}

:deep(.el-card) {
  --el-card-padding: 0;
}

:deep(.el-card__header) {
  @apply border-b border-slate-100 px-6 py-4;
}

:deep(.el-card__body) {
  @apply p-6;
}
</style>
