<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { getRecommendBySeason, getRecommendByOccasion, getRecommendByStyle, getRecommendPersonalized } from '@/api/recommend'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '@/api/interaction'
import { useRouter } from 'vue-router'
import { getWeatherNow } from '@/api/weather'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { Sunny, PartlyCloudy, Cloudy, Pouring, Lightning, Location, MagicStick, Coffee, Bicycle, Suitcase, Reading, ChatDotRound, EditPen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('season')
const activeOccasion = ref('日常') // 默认子场景
const outfits = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const currentPage = ref(1)
const hasMore = ref(true)
const PAGE_SIZE = 20
let loadKey = 0 // 请求版本号，切换 Tab 时递增，防止旧请求污染新数据


// 打字机相关
const aiReasoning = ref('')
const displayedReasoning = ref('')
let typingTimer: any = null
const startTyping = (text: string) => {
  displayedReasoning.value = ''
  if (typingTimer) clearInterval(typingTimer)
  let idx = 0
  typingTimer = setInterval(() => {
    if (idx < text.length) {
      displayedReasoning.value += text[idx++]
    } else {
      clearInterval(typingTimer)
    }
  }, 30)
}
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

const locationStatus = ref<'idle' | 'loading' | 'success' | 'failed'>('idle')
const detailedLocation = ref('')

const reverseGeocode = async (lat: number, lon: number) => {
  // 先查 localStorage 缓存（TTL 1小时）
  const cacheKey = `geocode_${lat.toFixed(2)}_${lon.toFixed(2)}`
  try {
    const cached = localStorage.getItem(cacheKey)
    if (cached) {
      const { value, expiry } = JSON.parse(cached)
      if (Date.now() < expiry) {
        detailedLocation.value = value
        locationStatus.value = 'success'
        return
      }
    }
  } catch (_) {}

  try {
    const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=14&addressdetails=1&accept-language=zh-CN,zh;q=0.9`)
    const data = await res.json()
    if (data && data.address) {
      const addr = data.address
      const prov = addr.province || addr.state || ''
      const c = addr.city || addr.town || addr.county || ''
      const dist = addr.suburb || addr.district || addr.village || ''
      let locStr = ''
      if (prov && c && prov !== c) locStr += prov + ' '
      if (c) locStr += c + ' '
      if (dist) locStr += dist + ' '
      if (!locStr) locStr = data.display_name?.split(',')[0] || '未知位置'
      detailedLocation.value = locStr.trim()
    } else {
      detailedLocation.value = '当前位置'
    }
    locationStatus.value = 'success'
    // 写入缓存（TTL 1小时）
    try {
      localStorage.setItem(cacheKey, JSON.stringify({
        value: detailedLocation.value,
        expiry: Date.now() + 60 * 60 * 1000
      }))
    } catch (_) {}
  } catch (e) {
    console.error('反向地理编码失败', e)
    detailedLocation.value = '当前位置'
    locationStatus.value = 'success'
  }
}

const handleCityChange = () => {
  showCityInput.value = false
  if (city.value) {
    userLocation.value.latitude = null
    userLocation.value.longitude = null
    detailedLocation.value = city.value
    locationStatus.value = 'success'
    loadWeather()
    loadData()
  }
}


const weatherInfo = ref<any>(null)

const weatherIcon = computed(() => {
  const desc = weatherInfo.value?.weatherDesc || ''
  if (desc.includes('晴')) return Sunny
  if (desc.includes('多云')) return PartlyCloudy
  if (desc.includes('雨')) return Pouring
  if (desc.includes('雷')) return Lightning
  if (desc.includes('雪') || desc.includes('雾')) return Cloudy
  return Sunny
})

const weatherBgClass = computed(() => {
  const desc = weatherInfo.value?.weatherDesc || ''
  if (desc.includes('晴')) return 'from-yellow-300 to-orange-400 shadow-orange-500/20'
  if (desc.includes('多云')) return 'from-blue-300 to-indigo-400 shadow-blue-500/20'
  if (desc.includes('雨') || desc.includes('雷')) return 'from-slate-400 to-slate-600 shadow-slate-500/20'
  if (desc.includes('雪') || desc.includes('雾')) return 'from-sky-200 to-blue-300 shadow-blue-400/20 text-blue-900'
  return 'from-yellow-300 to-orange-400 shadow-orange-500/20'
})
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const router = useRouter()
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)
const userLocation = ref(userStore.userLocation)

const occasions = [
  { name: '日常', icon: Coffee },
  { name: '图书馆', icon: Reading },
  { name: '运动', icon: Bicycle },
  { name: '约会', icon: MagicStick },
  { name: '面试/正式', icon: Suitcase }
]

const getLocation = () => {
  locationStatus.value = 'loading'
  // 如果 Store 中已有坐标，直接使用，不再触发浏览器弹窗
  if (userLocation.value.latitude && userLocation.value.longitude) {
    reverseGeocode(userLocation.value.latitude, userLocation.value.longitude)
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
        ElMessage.success('已定位到您所在的区域')
        reverseGeocode(lat, lon)
        loadWeather()
        loadData()
      },
      (error) => {
        console.error('获取位置失败:', error)
        locationStatus.value = 'failed'
        userLocation.value.latitude = null
        userLocation.value.longitude = null
        detailedLocation.value = ''
        loadWeather()
        loadData()
      },
      { enableHighAccuracy: true, timeout: 5000 }
    )
  } else {
    locationStatus.value = 'failed'
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

// 核心加载函数，reset=true 时清空列表并从第1页开始
const loadData = async (reset = true) => {
  if (reset) {
    outfits.value = []
    currentPage.value = 1
    hasMore.value = true
    aiReasoning.value = ''
    displayedReasoning.value = ''
    if (typingTimer) clearInterval(typingTimer)
  }
  if (!hasMore.value) return

  // 用版本号防竞态：每次 reset 时递增，如果请求完成时 key 已变则丢弃结果
  const myKey = reset ? ++loadKey : loadKey

  if (reset) {
    loading.value = true
    if (activeTab.value === 'personal') startThinking()
  } else {
    loadingMore.value = true
  }

  try {
    let res
    const commonParams = { 
      page: currentPage.value, 
      size: PAGE_SIZE,
      currentUserId: currentUserId.value
    }

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
      if (customScenario.value) params.scenario = customScenario.value
      res = await getRecommendPersonalized(params)
    }

    // 版本号对比：如果当前请求已过期（用户已切换 Tab）则丢弃结果
    if (myKey !== loadKey) return

    const records = (res as any)?.records || res?.data?.records || []
    outfits.value.push(...records)
    hasMore.value = records.length >= PAGE_SIZE
    currentPage.value++

    if (activeTab.value === 'personal' && reset && outfits.value.length > 0) {
      aiReasoning.value = outfits.value[0]?.recommendReason || ''
      startTyping(aiReasoning.value)
    }
  } catch (e) {
    console.error(e)
    } finally {
    loading.value = false
    loadingMore.value = false
    if (activeTab.value === 'personal') stopThinking()
  }
}

// 获取首选穿搭
const featuredOutfit = computed(() => outfits.value.length > 0 ? outfits.value[0] : null)
// 剩余的穿搭列表
const otherOutfits = computed(() => outfits.value.slice(1))

// 无限滚动触发
const loadMore = () => {
  if (!loadingMore.value && hasMore.value) {
    loadData(false)
  }
}

const handleLike = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
    router.push('/login')
    return
  }
  try {
    if (item.liked) {
      await unlikeOutfit(item.id, currentUserId.value!)
      item.likeCount = Math.max(0, (item.likeCount || 0) - 1)
      item.liked = false
    } else {
      await likeOutfit(item.id, currentUserId.value!)
      item.likeCount = (item.likeCount || 0) + 1
      item.liked = true
      ElMessage({ message: '点赞成功！', type: 'success', grouping: true })
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleFavorite = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
    router.push('/login')
    return
  }
  try {
    if (item.favorited) {
      await unfavoriteOutfit(item.id, currentUserId.value!)
      item.favCount = Math.max(0, (item.favCount || 0) - 1)
      item.favorited = false
    } else {
      await favoriteOutfit(item.id, currentUserId.value!)
      item.favCount = (item.favCount || 0) + 1
      item.favorited = true
      ElMessage({ message: '收藏成功！', type: 'success', grouping: true })
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 监听标签切换和场景切换，切换时重置并重新加载
watch([activeTab, activeOccasion], () => {
  loadData(true)
})

onMounted(() => {
  getLocation()
})

onUnmounted(() => {
  // 清除所有定时器，防止组件卸载后 setInterval 持续执行导致内存泄漏
  if (typingTimer) clearInterval(typingTimer)
  if (thinkingInterval) clearInterval(thinkingInterval)
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
            <div :class="['w-16 h-16 rounded-full bg-gradient-to-br flex items-center justify-center shadow-lg text-white transition-all duration-500', weatherBgClass]">
              <el-icon class="text-4xl drop-shadow-md"><component :is="weatherIcon" /></el-icon>
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
                  <span v-else class="flex items-center gap-1 hover:underline decoration-primary/50 underline-offset-4">
                    <template v-if="locationStatus === 'failed'">
                      <span class="text-red-400 dark:text-red-500 font-semibold" title="点击重试" @click.stop="getLocation">定位失败(点击重试)</span>
                    </template>
                    <template v-else-if="locationStatus === 'loading'">
                      <span class="animate-pulse">定位中...</span>
                    </template>
                    <template v-else>
                      {{ detailedLocation || weatherInfo?.location || city }}
                    </template>
                    <el-icon size="12"><EditPen /></el-icon>
                  </span>
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
            <p class="text-foreground/80 leading-relaxed font-medium">{{ displayedReasoning }}<span class="typing-cursor">|</span></p>
          </div>
        </div>
      </div>

      <div
        v-if="outfits.length > 0"
        v-infinite-scroll="loadMore"
        :infinite-scroll-disabled="loadingMore || !hasMore"
        :infinite-scroll-distance="100"
        class="pb-20"
      >
        <!-- 今日首选 / 英雄卡片 (仅在第一页显示) -->
        <div v-if="currentPage <= 2 && featuredOutfit" class="mb-12 animate-slide-up">
          <div class="relative group cursor-pointer" @click="router.push(`/outfit/${featuredOutfit.id}`)">
            <div class="absolute -inset-1 bg-gradient-to-r from-primary/30 to-indigo-500/30 rounded-[2.5rem] blur-2xl opacity-50 group-hover:opacity-100 transition duration-1000"></div>
            <div class="relative bg-white/40 dark:bg-black/40 backdrop-blur-3xl rounded-[2rem] border border-white/50 dark:border-white/10 overflow-hidden shadow-2xl flex flex-col lg:flex-row shadow-primary/10">
              <!-- 左侧大图 -->
              <div class="lg:w-1/2 h-[400px] lg:h-[500px] overflow-hidden relative">
                <img :src="featuredOutfit.thumbnailUrl || (featuredOutfit.imageUrls && featuredOutfit.imageUrls[0])" class="w-full h-full object-cover transform group-hover:scale-105 transition-transform duration-1000" />
                <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent"></div>
                <div class="absolute top-6 left-6 flex gap-2">
                  <span class="px-3 py-1.5 rounded-full bg-primary text-white text-xs font-black shadow-lg shadow-primary/40 flex items-center gap-1.5">
                    <el-icon><MagicStick /></el-icon> 今日首选
                  </span>
                  <span class="px-3 py-1.5 rounded-full bg-white/20 backdrop-blur-md text-white text-xs font-bold border border-white/30">
                    契合度 {{ featuredOutfit.matchScore }}%
                  </span>
                </div>
              </div>
              <!-- 右侧分析 -->
              <div class="lg:w-1/2 p-8 lg:p-12 flex flex-col justify-center">
                <div class="text-xs font-black text-primary tracking-widest uppercase mb-4 mb-2">Editor's Choice / 编辑精选</div>
                <h2 class="text-3xl lg:text-4xl font-black mb-6 leading-tight">{{ featuredOutfit.title }}</h2>
                
                <div class="space-y-6 mb-8">
                  <div class="flex items-start gap-4 p-4 rounded-2xl bg-primary/5 border border-primary/10">
                    <div class="w-10 h-10 rounded-xl bg-primary/20 flex items-center justify-center text-primary shrink-0">
                      <el-icon size="20"><ChatDotRound /></el-icon>
                    </div>
                    <div>
                      <div class="text-xs font-bold text-primary mb-1">主理人推荐理由</div>
                      <p class="text-sm text-foreground/70 leading-relaxed">{{ featuredOutfit.recommendReason }}</p>
                    </div>
                  </div>
                  
                  <!-- 匹配维度分析 -->
                  <div class="grid grid-cols-2 gap-4">
                    <div v-for="label in (featuredOutfit.matchLabels || ['极速通勤', '质感出众'])" :key="label" class="flex items-center gap-3">
                      <div class="w-1.5 h-1.5 rounded-full bg-primary"></div>
                      <span class="text-sm font-bold text-foreground/80">{{ label }}</span>
                    </div>
                  </div>
                </div>

                <div class="flex items-center justify-between pt-8 border-t border-border/50">
                  <div class="flex items-center gap-3">
                    <el-avatar :size="40" :src="featuredOutfit.userAvatar" />
                    <div>
                      <div class="text-sm font-black">{{ featuredOutfit.username }}</div>
                      <div class="text-[10px] text-muted-foreground uppercase tracking-widest">Featured Creator</div>
                    </div>
                  </div>
                  <el-button type="primary" round class="px-8 h-12 font-black shadow-xl shadow-primary/20" @click.stop="router.push(`/outfit/${featuredOutfit.id}`)">查看搭配详情</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="otherOutfits.length > 0">
          <h3 class="text-xl font-black mb-8 px-2 flex items-center gap-3">
            更多灵感发现 <span class="text-xs font-medium text-muted-foreground">More Inspirations</span>
          </h3>
          <MasonryGallery 
            :outfits="otherOutfits" 
            @like="handleLike" 
            @favorite="handleFavorite"
          />
        </div>
        <!-- 底部加载状态 -->
        <div class="mt-10 flex justify-center items-center gap-3 h-16">
          <template v-if="loadingMore">
            <div class="w-5 h-5 rounded-full border-2 border-primary border-t-transparent animate-spin"></div>
            <span class="text-sm text-muted-foreground">正在加载更多...</span>
          </template>
          <template v-else-if="!hasMore">
            <span class="text-sm text-muted-foreground/40 italic">— 到底啦，保持你的独特魅力 —</span>
          </template>
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

@keyframes slide-up {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-slide-up {
  animation: slide-up 0.4s ease-out;
}

/* 打字机光标闪烁动画 */
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.typing-cursor {
  display: inline-block;
  margin-left: 1px;
  font-weight: 300;
  color: var(--el-color-primary);
  animation: blink 0.9s step-end infinite;
}
</style>
