<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { getRecommendBySeason, getRecommendByOccasion, getRecommendByStyle } from '@/api/recommend'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '@/api/interaction'
import { useRouter } from 'vue-router'
import { getWeatherNow } from '@/api/weather'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { Sunny, PartlyCloudy, Cloudy, Pouring, Lightning, Location, MagicStick, Coffee, Bicycle, Suitcase, Reading, EditPen, ChatDotRound, Check, Right, Search, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AiAssistant from '@/components/AiAssistant.vue'

const activeTab = ref('season')
const activeOccasion = ref('日常') // 默认子场景
const outfits = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const currentPage = ref(1)
const hasMore = ref(true)
const PAGE_SIZE = 20
let loadKey = 0 // 请求版本号，切换 Tab 时递增，防止旧请求污染新数据




const city = ref('北京')
const showCityInput = ref(false)


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
  if (desc.includes('多云')) return 'from-blue-300 to-slate-400 shadow-slate-500/20'
  if (desc.includes('雨') || desc.includes('雷')) return 'from-slate-400 to-slate-600 shadow-slate-500/20'
  if (desc.includes('雪') || desc.includes('雾')) return 'from-sky-200 to-blue-300 shadow-blue-400/20'
  return 'from-yellow-300 to-orange-400 shadow-orange-500/20'
})

const iconColorClass = computed(() => {
  const desc = weatherInfo.value?.weatherDesc || ''
  if (desc.includes('晴')) return 'text-orange-950/70'
  if (desc.includes('多云')) return 'text-slate-950/70'
  if (desc.includes('雨') || desc.includes('雷')) return 'text-slate-900/70'
  if (desc.includes('雪') || desc.includes('雾')) return 'text-blue-950/70'
  return 'text-orange-950/70'
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

  }
  if (!hasMore.value) return

  // 用版本号防竞态：每次 reset 时递增，如果请求完成时 key 已变则丢弃结果
  const myKey = reset ? ++loadKey : loadKey

  if (reset) {
    loading.value = true

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
      const params: any = { ...commonParams, occasion: activeOccasion.value }
      if (userLocation.value.latitude && userLocation.value.longitude) {
        params.latitude = userLocation.value.latitude
        params.longitude = userLocation.value.longitude
      } else {
        params.city = city.value
      }
      res = await getRecommendByOccasion(params)
    } else if (activeTab.value === 'style') {
      res = await getRecommendByStyle(commonParams)
    }

    // 版本号对比：如果当前请求已过期（用户已切换 Tab）则丢弃结果
    if (myKey !== loadKey) return

    const records = (res as any)?.records || res?.data?.records || []
    outfits.value.push(...records)
    hasMore.value = records.length >= PAGE_SIZE
    currentPage.value++


  } catch (e) {
    console.error(e)
    } finally {
    loading.value = false
    loadingMore.value = false

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

// 自定义指令：自动聚焦
const vFocus = {
  mounted: (el: any) => {
    const input = el.querySelector('input') || el
    input.focus()
  }
}

onMounted(() => {
  getLocation()
})

onUnmounted(() => {
  // 清除所有定时器，防止组件卸载后 setInterval 持续执行导致内存泄漏

})</script>

<template>
  <div class="recommend-container p-6 pb-20 max-w-7xl mx-auto min-h-screen">
    <!-- 头部区域与天气看板 -->
    <!-- 头部区域与天气看板 -->
    <header class="mb-12 animate-fade-in">
      <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-8">
        <div class="space-y-2">
          <h1 class="text-4xl md:text-5xl font-black tracking-tight flex items-center gap-3">
            <span class="text-gradient">智能穿搭</span> 
            <span class="text-foreground/90">助手</span>
          </h1>
          <p class="text-lg text-muted-foreground font-medium max-w-md leading-relaxed">
            基于天气、场景与你的个性化喜好，发现今日最佳穿搭灵感。
          </p>
        </div>
        
        <!-- 天气卡片：多层级玻璃拟态设计 -->
        <div class="weather-dashboard animate-slide-up-slow">
          <div class="relative group">
            <!-- 背景装饰光晕 -->
            <div :class="['absolute -inset-4 blur-3xl opacity-20 group-hover:opacity-30 transition-opacity duration-1000 rounded-[3rem]', weatherBgClass]"></div>
            
            <div class="relative glass-card overflow-hidden p-6 md:p-8 flex flex-col md:flex-row items-center gap-8 min-w-[320px] md:min-w-[480px]">
              <!-- 左侧核心天气 -->
              <div class="flex items-center gap-6 shrink-0">
                <div :class="['w-20 h-20 rounded-2xl flex-center shadow-2xl transition-all duration-700 transform group-hover:scale-110 group-hover:rotate-3', weatherBgClass]">
                  <el-icon :class="['text-5xl drop-shadow-lg filter brightness-110', iconColorClass]"><component :is="weatherIcon" /></el-icon>
                </div>
                
                <div class="flex flex-col">
                  <div class="flex items-baseline gap-2">
                    <span class="text-5xl font-black tracking-tighter text-foreground">{{ weatherInfo?.temperature || '22°C' }}</span>
                    <span class="text-xl font-bold text-muted-foreground">{{ weatherInfo?.weatherDesc || '晴朗' }}</span>
                  </div>
                  
                  <!-- 地点选择器 -->
                  <div class="mt-2 group/loc">
                    <div 
                      v-if="!showCityInput" 
                      class="flex items-center gap-1.5 text-sm font-bold text-primary/80 hover:text-primary cursor-pointer transition-colors"
                      @click="showCityInput = true"
                    >
                      <el-icon><Location /></el-icon>
                      <span class="border-b border-dashed border-primary/30 group-hover/loc:border-primary/60 transition-all">
                        <template v-if="locationStatus === 'failed'">定位失败 (点击重试)</template>
                        <template v-else-if="locationStatus === 'loading'">定位中...</template>
                        <template v-else>{{ detailedLocation || weatherInfo?.location || city }}</template>
                      </span>
                      <el-icon size="12" class="opacity-0 group-hover/loc:opacity-100 transition-opacity"><EditPen /></el-icon>
                    </div>
                    
                    <el-input 
                      v-else 
                      v-model="city" 
                      size="small" 
                      placeholder="输入城市名"
                      class="city-input-premium"
                      @blur="handleCityChange" 
                      @keyup.enter="handleCityChange"
                      v-focus
                    />
                  </div>
                </div>
              </div>

              <!-- 右侧穿衣建议 -->
              <div class="flex-1 md:border-l border-border/50 md:pl-8 space-y-3">
                <div class="flex items-center gap-2">
                  <div class="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-[10px] font-black uppercase tracking-widest">
                    {{ weatherInfo?.dressIndex || '舒适' }}
                  </div>
                  <span class="text-xs font-bold text-muted-foreground uppercase tracking-wider">今日穿衣指数</span>
                </div>
                <p class="text-sm text-foreground/80 leading-relaxed font-medium">
                  {{ weatherInfo?.suggestion || '温度非常宜人，短袖配合薄衬衫，或是裙装都非常适合校园活动。' }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 推荐维度选择 -->
    <div class="sticky top-0 z-20 bg-background/80 backdrop-blur-xl py-6 mb-10 border-b border-border/50">
      <div class="flex items-center justify-between">
        <el-tabs v-model="activeTab" class="premium-tabs">
          <el-tab-pane label="天气匹配" name="season">
            <template #label>
              <span class="flex items-center gap-2">
                <el-icon><Sunny /></el-icon> 天气匹配
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="场景推荐" name="occasion">
            <template #label>
              <span class="flex items-center gap-2">
                <el-icon><Coffee /></el-icon> 场景推荐
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="风格发现" name="style">
            <template #label>
              <span class="flex items-center gap-2">
                <el-icon><MagicStick /></el-icon> 风格发现
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 子场景选择器 (仅在场景推荐下显示) -->
      <transition 
        enter-active-class="transition duration-300 ease-out"
        enter-from-class="transform -translate-y-4 opacity-0"
        enter-to-class="transform translate-y-0 opacity-100"
        leave-active-class="transition duration-200 ease-in"
        leave-from-class="transform translate-y-0 opacity-100"
        leave-to-class="transform -translate-y-4 opacity-0"
      >
        <div v-if="activeTab === 'occasion'" class="flex flex-wrap gap-3 mt-6">
          <div 
            v-for="occ in occasions" 
            :key="occ.name"
            :class="[
              'premium-pill',
              activeOccasion === occ.name ? 'active' : ''
            ]"
            @click="activeOccasion = occ.name"
          >
            <el-icon class="text-lg"><component :is="occ.icon" /></el-icon>
            <span class="font-bold tracking-wide">{{ occ.name }}</span>
          </div>
        </div>
      </transition>
    </div>

    <!-- 瀑布流展示区 -->
    <div v-if="loading" class="py-32 flex flex-col items-center justify-center animate-fade-in gap-8">
      <div class="relative w-24 h-24">
        <div class="absolute inset-0 border-4 border-primary/20 rounded-full"></div>
        <div class="absolute inset-0 border-4 border-primary border-t-transparent rounded-full animate-spin"></div>
        <div class="absolute inset-0 flex-center">
          <el-icon class="text-3xl text-primary animate-pulse"><MagicStick /></el-icon>
        </div>
      </div>
      <div class="text-center space-y-2">
        <h3 class="text-xl font-black text-foreground">正在计算最佳穿搭...</h3>
        <p class="text-sm text-muted-foreground">AI 正在根据当前天气和场景为您筛选最合适的灵感</p>
      </div>
      <el-skeleton :rows="3" animated class="w-full max-w-2xl mt-8" />
    </div>
    
    <div v-else class="animate-fade-in">
      <el-scrollbar
        v-if="outfits.length > 0"
        height="max(600px, calc(100vh - 280px))"
        class="recommend-scrollbar"
      >
        <div
          v-infinite-scroll="loadMore"
          :infinite-scroll-disabled="loadingMore || !hasMore"
          :infinite-scroll-distance="100"
          :infinite-scroll-immediate="false"
          class="pb-20"
        >
        <!-- 今日首选 / 英雄卡片 (仅在第一页显示) -->
        <div v-if="currentPage <= 2 && featuredOutfit" class="mb-16 animate-slide-up">
          <div class="hero-card-container group" @click="router.push(`/outfit/${featuredOutfit.id}`)">
            <!-- 悬浮光影层 -->
            <div class="hero-glow-layer"></div>
            
            <div class="hero-content-wrapper">
              <!-- 左侧大图：带有视差感和渐变遮罩 -->
              <div class="hero-image-section">
                <img 
                  :src="featuredOutfit.thumbnailUrl || (featuredOutfit.imageUrls && featuredOutfit.imageUrls[0])" 
                  class="hero-image" 
                />
                <div class="hero-image-overlay"></div>
                
                <!-- 浮动标签 -->
                <div class="absolute top-8 left-8 flex flex-col gap-3">
                  <div class="badge-featured">
                    <el-icon class="animate-bounce"><MagicStick /></el-icon> 
                    <span>今日首选</span>
                  </div>
                  <div class="badge-score">
                    契合度 <span class="text-white font-black ml-1">{{ featuredOutfit.matchScore }}%</span>
                  </div>
                </div>
              </div>

              <!-- 右侧分析：精致排版与微交互 -->
              <div class="hero-info-section">
                <div class="space-y-6">
                  <div>
                    <div class="editor-label">Editor's Choice / 编辑精选</div>
                    <h2 class="hero-title">{{ featuredOutfit.title }}</h2>
                  </div>
                  
                  <div class="recommend-box">
                    <div class="recommend-icon">
                      <el-icon><ChatDotRound /></el-icon>
                    </div>
                    <div class="flex-1">
                      <div class="recommend-label">主理人推荐理由</div>
                      <p class="recommend-text">{{ featuredOutfit.recommendReason }}</p>
                    </div>
                  </div>
                  
                  <!-- 匹配维度分析：网格布局 -->
                  <div class="grid grid-cols-2 gap-y-4 gap-x-6">
                    <div v-for="label in (featuredOutfit.matchLabels || ['极速通勤', '质感出众', '活力校园', '百搭简约'])" :key="label" class="match-tag">
                      <div class="tag-dot"></div>
                      <span class="tag-text">{{ label }}</span>
                    </div>
                  </div>
                </div>

                <!-- 底部用户信息与操作 -->
                <div class="hero-footer">
                  <div class="creator-profile">
                    <div class="relative">
                      <el-avatar :size="48" :src="featuredOutfit.userAvatar" class="border-2 border-primary/20" />
                      <div class="absolute -bottom-1 -right-1 w-5 h-5 bg-primary rounded-full border-2 border-white flex-center">
                        <el-icon size="10" class="text-white"><Check /></el-icon>
                      </div>
                    </div>
                    <div>
                      <div class="creator-name">{{ featuredOutfit.username }}</div>
                      <div class="creator-title">Featured Creator</div>
                    </div>
                  </div>
                  
                  <el-button type="primary" class="hero-cta-button" @click.stop="router.push(`/outfit/${featuredOutfit.id}`)">
                    查看详情
                    <el-icon class="ml-2 group-hover:translate-x-1 transition-transform"><Right /></el-icon>
                  </el-button>
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
      </el-scrollbar>
      <div v-else class="empty-state-container animate-fade-in">
        <div class="relative group">
          <div class="absolute -inset-10 bg-primary/5 blur-3xl rounded-full opacity-50 group-hover:opacity-100 transition duration-1000"></div>
          <el-empty 
            description="暂时没有找到相关穿搭，请尝试更换场景词喔~" 
            :image-size="200"
          >
            <template #image>
              <div class="relative flex-center">
                <el-icon size="120" class="text-primary/10"><Search /></el-icon>
                <el-icon size="40" class="absolute text-primary/40 animate-bounce"><QuestionFilled /></el-icon>
              </div>
            </template>
          </el-empty>
        </div>
      </div>
    </div>
    <AiAssistant />
  </div>
</template>

<style scoped>
.recommend-container {
  background: 
    radial-gradient(circle at 0% 0%, rgba(var(--primary-rgb), 0.05) 0%, transparent 40%),
    radial-gradient(circle at 100% 100%, rgba(var(--primary-rgb), 0.05) 0%, transparent 40%),
    linear-gradient(to bottom, transparent, rgba(var(--primary-rgb), 0.01) 50%, transparent 100%);
}

.weather-dashboard {
  perspective: 1000px;
}

.city-input-premium :deep(.el-input__wrapper) {
  @apply bg-background/50 backdrop-blur-sm border-primary/20 shadow-none px-3 h-8;
}

/* Premium Tabs */
.premium-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.premium-tabs :deep(.el-tabs__active-bar) {
  @apply bg-primary h-1 rounded-full;
  bottom: 4px;
}

.premium-tabs :deep(.el-tabs__item) {
  @apply text-muted-foreground font-bold transition-all duration-300 h-12 flex items-center px-6;
}

.premium-tabs :deep(.el-tabs__item.is-active) {
  @apply text-primary scale-105;
}

/* Premium Pills */
.premium-pill {
  @apply px-5 py-2.5 rounded-2xl text-sm border border-border/40 bg-card/40 backdrop-blur-md cursor-pointer 
         transition-all duration-500 flex items-center gap-2 shadow-sm hover:shadow-md hover:border-primary/30 
         hover:-translate-y-0.5 active:scale-95;
}

.premium-pill.active {
  @apply bg-primary text-primary-foreground border-primary shadow-xl shadow-primary/20 scale-105;
}

/* Hero Card Styles */
.hero-card-container {
  @apply relative cursor-pointer;
}

.hero-glow-layer {
  @apply absolute -inset-1 bg-gradient-to-r from-primary/30 to-primary/10 rounded-[2.5rem] blur-2xl 
         opacity-40 group-hover:opacity-70 transition duration-1000;
}

.hero-content-wrapper {
  @apply relative bg-card/60 dark:bg-black/60 backdrop-blur-3xl rounded-[2.5rem] border border-white/50 
         dark:border-white/10 overflow-hidden shadow-2xl flex flex-col lg:flex-row shadow-primary/10 
         transition-transform duration-700 hover:scale-[1.01];
}

.hero-image-section {
  @apply lg:w-1/2 h-[450px] lg:h-[600px] overflow-hidden relative;
}

.hero-image {
  @apply w-full h-full object-cover transform group-hover:scale-110 transition-transform duration-[2s] ease-out;
}

.hero-image-overlay {
  @apply absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-60 group-hover:opacity-40 transition-opacity;
}

.badge-featured {
  @apply px-4 py-2 rounded-xl bg-primary text-white text-xs font-black shadow-2xl shadow-primary/40 
         flex items-center gap-2 backdrop-blur-md border border-white/20;
}

.badge-score {
  @apply px-4 py-2 rounded-xl bg-black/40 backdrop-blur-md text-white/80 text-xs font-bold border border-white/10;
}

.hero-info-section {
  @apply lg:w-1/2 p-10 lg:p-14 flex flex-col justify-between;
}

.editor-label {
  @apply text-xs font-black text-primary tracking-[0.2em] uppercase mb-3;
}

.hero-title {
  @apply text-4xl lg:text-5xl font-black mb-2 leading-[1.1] text-foreground tracking-tight;
}

.recommend-box {
  @apply flex items-start gap-5 p-6 rounded-3xl bg-primary/5 border border-primary/10 transition-colors group-hover:bg-primary/10;
}

.recommend-icon {
  @apply w-12 h-12 rounded-2xl bg-primary/20 flex-center text-primary shrink-0 shadow-inner;
}

.recommend-label {
  @apply text-xs font-black text-primary mb-2 uppercase tracking-wider;
}

.recommend-text {
  @apply text-sm text-foreground/80 leading-relaxed font-medium italic;
}

.match-tag {
  @apply flex items-center gap-3 transition-transform hover:translate-x-1;
}

.tag-dot {
  @apply w-2 h-2 rounded-full bg-primary shadow-sm shadow-primary/50;
}

.tag-text {
  @apply text-sm font-bold text-foreground/80;
}

.hero-footer {
  @apply flex items-center justify-between pt-10 border-t border-border/50 mt-8;
}

.creator-profile {
  @apply flex items-center gap-4;
}

.creator-name {
  @apply text-base font-black text-foreground;
}

.creator-title {
  @apply text-[10px] text-muted-foreground uppercase tracking-[0.15em] font-bold;
}

.hero-cta-button {
  @apply h-14 px-10 rounded-2xl font-black text-base shadow-2xl shadow-primary/30 transition-all 
         hover:shadow-primary/50 hover:scale-105 active:scale-95 !border-none;
}

.text-gradient {
  background: linear-gradient(135deg, hsl(var(--primary)), #4a5a6a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* Scrollbar Style */
.recommend-scrollbar :deep(.el-scrollbar__bar.is-vertical) {
  width: 4px;
}

.recommend-scrollbar :deep(.el-scrollbar__thumb) {
  @apply bg-primary/20 hover:bg-primary/40 transition-colors;
}

/* Empty State */
.empty-state-container {
  @apply flex flex-col items-center justify-center py-32 bg-card/30 backdrop-blur-sm rounded-[3rem] 
         border border-dashed border-border/50 relative overflow-hidden;
}

/* Animations */
@keyframes fade-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slide-up-slow {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fade-in 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.animate-slide-up-slow {
  animation: slide-up-slow 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.animate-slide-up {
  animation: slide-up 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}


</style>
