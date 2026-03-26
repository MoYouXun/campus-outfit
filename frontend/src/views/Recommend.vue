<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getRecommendBySeason, getRecommendByOccasion, getRecommendByStyle, getRecommendPersonalized } from '@/api/recommend'
import { getWeatherNow } from '@/api/weather'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { Sunny, Location, MagicStick, Coffee, Bicycle, Suitcase, Reading, ChatDotRound, EditPen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('season')
const activeOccasion = ref('日常') // 默认子场景
const outfits = ref<any[]>([])
const loading = ref(false)
const aiReasoning = ref('')
const thinkingSteps = [
  '🤖 正在解析场景诉求...',
  '☁️ 结合当地气象模型分析...',
  '👗 检索匹配的最佳单品...',
  '✨ 极速生成最佳穿搭组合...'
]
const currentThinkingStep = ref(0)
let thinkingInterval: any

const startThinking = () => {
  currentThinkingStep.value = 0
  if (thinkingInterval) clearInterval(thinkingInterval)
  thinkingInterval = setInterval(() => {
    currentThinkingStep.value = (currentThinkingStep.value + 1) % thinkingSteps.length
  }, 1200)
}

const stopThinking = () => {
  if (thinkingInterval) clearInterval(thinkingInterval)
}

const city = ref('北京')
const showCityInput = ref(false)
const customScenario = ref('')

const handleCityChange = () => {
  showCityInput.value = false
  if (city.value) {
    userLocation.value.latitude = null
    userLocation.value.longitude = null
    loadWeather()
    loadData()
  }
}

const weatherInfo = ref<any>(null)
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const userLocation = ref(userStore.userLocation)

const occasions = [
  { name: '日常', icon: Coffee },
  { name: '图书馆', icon: Reading },
  { name: '运动', icon: Bicycle },
  { name: '约会', icon: MagicStick },
  { name: '面试/正式', icon: Suitcase }
]

const getLocation = () => {
  // 如果 Store 中已有坐标，直接使用，不再触发浏览器弹窗
  if (userLocation.value.latitude && userLocation.value.longitude) {
    loadWeather()
    loadData()
    return
  }

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = position.coords.latitude
        const lon = position.coords.longitude
        userStore.setLocation(lat, lon)
        userLocation.value = userStore.userLocation
        ElMessage.success('已同步您的地理位置')
        loadWeather()
        loadData()
      },
      (error) => {
        console.error('获取位置失败:', error)
        // ... 其他错误处理逻辑保持不变
        userLocation.value.latitude = null
        userLocation.value.longitude = null
        loadWeather()
        loadData()
      },
      { enableHighAccuracy: false, timeout: 5000 }
    )
  } else {
    loadWeather()
    loadData()
  }
}

const loadWeather = async () => {
  try {
    const params: any = {}
    if (userLocation.value.latitude && userLocation.value.longitude) {
      params.latitude = userLocation.value.latitude
      params.longitude = userLocation.value.longitude
    } else {
      params.city = city.value
    }
    const res = await getWeatherNow(params)
    weatherInfo.value = res
  } catch (e) {
    console.error('获取天气失败', e)
    // 降级显示
    weatherInfo.value = {
      location: city.value,
      temperature: '22°C',
      weatherDesc: '多云',
      dressIndex: '舒适',
      suggestion: '系统暂时无法获取实时天气，已为您展示北京的基本穿搭建议。'
    }
  }
}

const loadData = async () => {
  loading.value = true
  aiReasoning.value = ''
  if (activeTab.value === 'personal') {
    startThinking()
  }
  try {
    let res
    const commonParams = { page: 1, size: 20 }
    
    if (activeTab.value === 'season') {
      const params: any = { ...commonParams }
      if (userLocation.value.latitude && userLocation.value.longitude) {
        params.latitude = userLocation.value.latitude
        params.longitude = userLocation.value.longitude
      } else {
        params.city = city.value
      }
      res = await getRecommendBySeason(params)
    } else if (activeTab.value === 'occasion') {
      res = await getRecommendByOccasion({ ...commonParams, occasion: activeOccasion.value })
    } else if (activeTab.value === 'style') {
      res = await getRecommendByStyle(commonParams)
    } else {
      const params: any = { ...commonParams }
      if (userLocation.value.latitude && userLocation.value.longitude) {
        params.latitude = userLocation.value.latitude
        params.longitude = userLocation.value.longitude
      } else {
        params.city = city.value
      }
      if (customScenario.value) {
        params.scenario = customScenario.value
      }
      res = await getRecommendPersonalized(params)
    }
    
    // axios 拦截器已经在响应成功时 unwrapped 了第一层 {code, data, message}
    // 所以这里的 res 直接就是 IPage 对象，具有 records 属性
    outfits.value = (res as any)?.records || res?.data?.records || []
    if (activeTab.value === 'personal' && outfits.value.length > 0) {
      aiReasoning.value = outfits.value[0]?.recommendReason || ''
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    if (activeTab.value === 'personal') {
      stopThinking()
    }
  }
}

// 监听标签切换和场景切换
watch([activeTab, activeOccasion], () => {
  loadData()
})

onMounted(() => {
  getLocation()
})</script>

<template>
  <div class="recommend-container p-6 pb-20 max-w-7xl mx-auto min-h-screen">
    <!-- 头部区域与天气看板 -->
    <header class="mb-10 animate-fade-in">
      <div class="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h1 class="text-4xl font-extrabold mb-2 tracking-tight">
            <span class="text-gradient">智能穿搭</span> 助手
          </h1>
          <p class="text-muted-foreground font-medium">基于天气、场景与你的个性化喜好，发现今日最佳穿搭。</p>
        </div>
        
        <!-- 天气卡片 -->
        <div class="weather-card animate-slide-up-slow glass border border-white/40 dark:border-white/10 rounded-2xl p-6 flex flex-col sm:flex-row items-center justify-between gap-6 relative overflow-hidden">
          <div class="absolute right-0 top-0 w-32 h-32 bg-yellow-400/10 dark:bg-yellow-400/5 rounded-bl-full pointer-events-none"></div>
          <div class="flex items-center gap-4">
            <div class="w-16 h-16 rounded-full bg-gradient-to-br from-yellow-300 to-orange-400 flex-center shadow-lg shadow-orange-500/20 text-white">
              <el-icon class="text-3xl"><Sunny /></el-icon>
            </div>
            <div>
              <div class="flex items-baseline gap-2">
                <span class="text-3xl font-bold tracking-tighter">{{ weatherInfo?.temperature || '22°C' }}</span>
                <span class="text-lg font-medium text-muted-foreground">{{ weatherInfo?.weatherDesc || '晴朗' }}</span>
              </div>
              <div class="flex items-center gap-1 text-sm font-semibold text-primary mt-1">
                <el-icon class="text-primary"><Location /></el-icon>
                <div class="inline-block cursor-pointer" @click="showCityInput = true">
                  <el-input 
                    v-if="showCityInput" 
                    v-model="city" 
                    size="small" 
                    style="width: 120px" 
                    placeholder="输入城市名"
                    @blur="handleCityChange" 
                    @keyup.enter="handleCityChange" 
                    @click.stop 
                  />
                  <span v-else class="flex items-center gap-1 hover:underline decoration-primary/50 underline-offset-4">{{ weatherInfo?.location || city }} <el-icon size="12"><EditPen /></el-icon></span>
                </div>
              </div>
            </div>
          </div>
          <div class="sm:w-1/2 text-sm text-foreground/80 leading-relaxed border-l-2 border-border pl-4">
            <div class="text-xs uppercase tracking-wider font-bold text-primary mb-1">穿衣指数：{{ weatherInfo?.dressIndex || '舒适' }}</div>
            {{ weatherInfo?.suggestion || '温度非常宜人，短袖配合薄衬衫，或是裙装都非常适合校园活动。' }}
          </div>
        </div>
      </div>
    </header>

    <!-- 推荐维度选择 -->
    <div class="sticky top-0 z-20 bg-background/80 backdrop-blur-xl py-4 mb-8">
      <div class="flex items-center justify-between">
        <el-tabs v-model="activeTab" class="custom-tabs">
          <el-tab-pane label="天气匹配" name="season"></el-tab-pane>
          <el-tab-pane label="场景推荐" name="occasion"></el-tab-pane>
          <el-tab-pane label="风格发现" name="style"></el-tab-pane>
          <el-tab-pane label="个性化定制" name="personal"></el-tab-pane>
        </el-tabs>
      </div>

      <!-- 子场景选择器 (仅在场景推荐下显示) -->
      <transition name="el-zoom-in-top">
        <div v-if="activeTab === 'occasion'" class="flex flex-wrap gap-2 mt-4 animate-fade-in">
          <div 
            v-for="occ in occasions" 
            :key="occ.name"
            :class="[
              'occasion-pill',
              activeOccasion === occ.name ? 'active' : ''
            ]"
            @click="activeOccasion = occ.name"
          >
            <el-icon class="mr-1.5"><component :is="occ.icon" /></el-icon>
            {{ occ.name }}
          </div>
        </div>
      </transition>

      <!-- 个性化场景输入 (仅在个性化推荐下显示) -->
      <transition name="el-zoom-in-top">
        <div v-if="activeTab === 'personal'" class="mt-4 animate-fade-in bg-background/40 backdrop-blur-md p-4 rounded-xl border border-border/50 shadow-sm">
          <div class="flex gap-4">
            <el-input 
              v-model="customScenario" 
              placeholder="请描述您的具体场景（例如：明天去参加互联网大厂面试，怎么穿能展现专业和活力？）" 
              clearable
              @keyup.enter="loadData"
              class="custom-scenario-input"
            >
              <template #prefix>
                <el-icon><ChatDotRound /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" :icon="MagicStick" @click="loadData" class="px-6 rounded-lg font-bold shadow-lg shadow-primary/30">AI 推荐</el-button>
          </div>
        </div>
      </transition>
    </div>

    <!-- 瀑布流展示区 -->
    <div v-if="loading" class="py-32 flex flex-col items-center justify-center animate-fade-in gap-5">
      <template v-if="activeTab === 'personal'">
        <div class="relative w-24 h-24 mb-2">
          <div class="absolute inset-0 border-4 border-primary/20 rounded-full animate-ping"></div>
          <div class="absolute inset-2 border-4 border-primary rounded-full border-t-transparent animate-spin"></div>
          <div class="absolute inset-0 flex items-center justify-center text-3xl">✨</div>
        </div>
        <div class="text-xl font-bold text-gradient transition-all duration-300">{{ thinkingSteps[currentThinkingStep] }}</div>
        <p class="text-sm text-foreground/60">专属 AI 搭配管家正在为您调度衣橱与气象数据...</p>
      </template>
      <template v-else>
        <el-skeleton :rows="5" animated class="w-full max-w-xl" />
        <p class="text-sm text-muted-foreground animate-pulse mt-4">正在计算推荐穿搭组合...</p>
      </template>
    </div>
    
    <div v-else class="animate-fade-in">
      <!-- AI 推理画板 (仅在个人推荐且有数据时显示) -->
      <div v-if="activeTab === 'personal' && aiReasoning && outfits.length > 0" class="mb-8 p-6 bg-background/60 backdrop-blur-xl rounded-2xl border border-primary/30 shadow-[0_4px_24px_rgba(var(--primary-rgb),0.08)] animate-slide-up">
        <div class="flex items-start gap-4">
          <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/20 to-primary/10 flex items-center justify-center shrink-0 border border-primary/20 shadow-inner">
            <el-icon class="text-2xl text-primary"><ChatDotRound /></el-icon>
          </div>
          <div class="flex-1">
            <h3 class="text-lg font-bold mb-2 flex items-center gap-2">
              穿搭解析顾问 
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-primary/10 text-primary border border-primary/20 tracking-wider">AI GENERATED</span>
            </h3>
            <p class="text-foreground/80 leading-relaxed font-medium">{{ aiReasoning }}</p>
          </div>
        </div>
      </div>

      <div v-if="outfits.length > 0">
        <MasonryGallery :outfits="outfits" />
        <div class="mt-12 text-center text-muted-foreground/40 text-sm italic font-medium">
          — 到底啦，保持你的独特魅力 —
        </div>
      </div>
      <div v-else class="flex flex-col items-center justify-center py-32 bg-secondary/20 rounded-3xl border border-dashed border-border">
        <el-empty description="暂时没有找到相关穿搭，请尝试更换场景词喔~" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.recommend-container {
  background: radial-gradient(circle at 0% 0%, rgba(var(--primary-rgb), 0.03) 0%, transparent 50%),
              radial-gradient(circle at 100% 100%, rgba(var(--primary-rgb), 0.03) 0%, transparent 50%);
}

.weather-card {
  @apply bg-background/40 backdrop-blur-md border border-border/50 p-4 rounded-2xl shadow-sm hover:shadow-md transition-shadow;
}

.weather-icon-box {
  @apply w-12 h-12 rounded-xl bg-yellow-500/10 flex items-center justify-center border border-yellow-500/20;
}

.occasion-pill {
  @apply px-4 py-2 rounded-full text-sm font-medium border border-border/60 bg-background/50 cursor-pointer transition-all duration-300 hover:bg-primary/5 hover:border-primary/30 flex items-center;
}

.occasion-pill.active {
  @apply bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/20 scale-105;
}

.text-gradient {
  background: linear-gradient(to right, var(--el-color-primary), #6366f1);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

:deep(.custom-tabs .el-tabs__nav-wrap::after) {
  background-color: transparent;
}

:deep(.custom-tabs .el-tabs__active-bar) {
  background-color: var(--el-color-primary);
  height: 3px;
}

:deep(.custom-tabs .el-tabs__item.is-active) {
  color: var(--el-color-primary);
  font-weight: 600;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slide-up-slow {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fade-in 0.5s ease-out;
}

.animate-slide-up-slow {
  animation: slide-up-slow 0.6s ease-out;
}
</style>
