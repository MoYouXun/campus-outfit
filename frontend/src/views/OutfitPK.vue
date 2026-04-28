<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { pkOutfits } from '@/api/ai'
import { getWardrobeList } from '@/api/wardrobe'
import { getMyOutfits, getFavoriteOutfits } from '@/api/outfit'
import { ElMessage } from 'element-plus'
import { MagicStick, ChatDotRound, Trophy, Plus, Check, Collection, Postcard, ShoppingBag, RefreshRight, ArrowRight } from '@element-plus/icons-vue'

// 响应式变量
const imageAUrl = ref('')
const imageBUrl = ref('')
const scene = ref('校园路演/学术演讲')
const loading = ref(false)
const pkResult = ref<any>(null)
const radarChartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

// 资源选择弹窗相关
const isSelectionDialogVisible = ref(false)
const selectionLoading = ref(false)
const activeTab = ref<'posts' | 'favorites' | 'wardrobe'>('posts')
const targetSlot = ref<'A' | 'B'>('A')

const posts = ref<any[]>([])
const favorites = ref<any[]>([])
const wardrobeItems = ref<any[]>([])

/**
 * 监听 Tab 切换并获取数据
 */
watch(activeTab, () => {
  fetchSelectionData()
})

/**
 * 打开选择弹窗
 */
const openSelectionDialog = (target: 'A' | 'B') => {
  targetSlot.value = target
  isSelectionDialogVisible.value = true
  fetchSelectionData()
}

/**
 * 获取素材数据
 */
const fetchSelectionData = async () => {
  // 仅在列表为空时才请求
  if (activeTab.value === 'posts' && posts.value.length > 0) return
  if (activeTab.value === 'favorites' && favorites.value.length > 0) return
  if (activeTab.value === 'wardrobe' && wardrobeItems.value.length > 0) return

  selectionLoading.value = true
  try {
    if (activeTab.value === 'posts') {
      const res: any = await getMyOutfits({ page: 1, size: 50 })
      posts.value = res.records || []
    } else if (activeTab.value === 'favorites') {
      const res: any = await getFavoriteOutfits()
      favorites.value = res || []
    } else if (activeTab.value === 'wardrobe') {
      const res: any = await getWardrobeList()
      wardrobeItems.value = res || []
    }
  } catch (e: any) {
    ElMessage.error('获取资源列表失败')
  } finally {
    selectionLoading.value = false
  }
}

/**
 * 获取当前选项卡对应的列表数据
 */
const getCurrentList = computed(() => {
  if (activeTab.value === 'posts') return posts.value
  if (activeTab.value === 'favorites') return favorites.value
  return wardrobeItems.value
})

/**
 * 获取具体某一项的图片 URL
 */
const getItemUrl = (item: any) => {
  if (activeTab.value === 'wardrobe') return item.originalImageUrl
  return item.imageUrls && item.imageUrls.length > 0 ? item.imageUrls[0] : item.thumbnailUrl
}

/**
 * 选中素材
 */
const handleSelectItem = (item: any) => {
  const url = getItemUrl(item)
  if (targetSlot.value === 'A') {
    imageAUrl.value = url
  } else {
    imageBUrl.value = url
  }
  isSelectionDialogVisible.value = false
  ElMessage.success(`已设置搭配 ${targetSlot.value}`)
}

/**
 * 渲染雷达图
 */
const renderRadarChart = (radarData: any) => {
  if (!radarChartRef.value) return
  if (myChart) myChart.dispose()
  myChart = echarts.init(radarChartRef.value)

  const isDark = document.documentElement.classList.contains('dark')

  const option = {
    color: ['#409EFF', '#F56C6C'],
    tooltip: {
      show: true,
      backgroundColor: isDark ? 'rgba(30, 30, 30, 0.8)' : 'rgba(255, 255, 255, 0.8)',
      backdropFilter: 'blur(10px)',
      borderWidth: 0,
      textStyle: { color: isDark ? '#eee' : '#333' }
    },
    legend: { 
      data: ['搭配 A', '搭配 B'], 
      bottom: 0,
      itemGap: 40,
      textStyle: { 
        fontWeight: 'bold',
        color: isDark ? '#aaa' : '#666',
        fontSize: 14
      }
    },
    radar: {
      indicator: radarData.dimensions.map((dim: string) => ({ name: dim, max: 100 })),
      radius: '65%',
      center: ['50%', '45%'],
      splitNumber: 5,
      name: {
        textStyle: {
          color: isDark ? '#eee' : '#444',
          fontSize: 13,
          fontWeight: '600',
          padding: [5, 10],
          backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.02)',
          borderRadius: 8
        }
      },
      splitLine: {
        lineStyle: {
          color: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)',
          width: 1
        }
      },
      splitArea: {
        areaStyle: {
          color: isDark ? ['rgba(255,255,255,0.02)', 'transparent'] : ['rgba(0,0,0,0.01)', 'transparent']
        }
      },
      axisLine: {
        lineStyle: {
          color: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)'
        }
      }
    },
    series: [
      {
        type: 'radar',
        symbolSize: 6,
        data: [
          { 
            value: radarData.scoresA, 
            name: '搭配 A', 
            areaStyle: { 
              color: 'rgba(64, 158, 255, 0.2)'
            },
            lineStyle: { width: 3, shadowBlur: 10, shadowColor: 'rgba(64, 158, 255, 0.3)' }
          },
          { 
            value: radarData.scoresB, 
            name: '搭配 B', 
            areaStyle: { 
              color: 'rgba(245, 108, 108, 0.2)'
            },
            lineStyle: { width: 3, shadowBlur: 10, shadowColor: 'rgba(245, 108, 108, 0.3)' }
          }
        ],
        animationDuration: 1200,
        animationEasing: 'exponentialOut'
      }
    ]
  }
  myChart.setOption(option)
}

/**
 * 重置 PK 状态
 */
const resetPK = () => {
  pkResult.value = null
  imageAUrl.value = ''
  imageBUrl.value = ''
  if (myChart) {
    myChart.dispose()
    myChart = null
  }
  ElMessage.success('已重置对决状态')
}

/**
 * 开始 PK 逻辑
 */
const startPK = async () => {
  if (!imageAUrl.value || !imageBUrl.value || !scene.value) {
    ElMessage.warning('请确保图片已选择并填写场景')
    return
  }
  loading.value = true
  try {
    const res: any = await pkOutfits({
      imageAUrl: imageAUrl.value,
      imageBUrl: imageBUrl.value,
      scene: scene.value
    })
    pkResult.value = res
    await nextTick()
    renderRadarChart(res.radarData)
    ElMessage.success('AI 对决分析完成！')
  } catch (error: any) {
    ElMessage.error(error.message || '分析失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="pk-page max-w-7xl mx-auto px-4 py-16 relative overflow-hidden">
    <!-- 背景装饰流光 -->
    <div class="absolute top-0 left-1/4 w-96 h-96 bg-primary/10 rounded-full blur-[120px] -z-10 animate-pulse"></div>
    <div class="absolute bottom-0 right-1/4 w-96 h-96 bg-accent/10 rounded-full blur-[120px] -z-10 animate-pulse" style="animation-delay: 2s"></div>

    <div class="text-center mb-16 animate-fade-in">
      <div class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-bold uppercase tracking-widest mb-6">
        <el-icon><MagicStick /></el-icon> AI Powered Analysis
      </div>
      <h1 class="text-5xl font-black text-foreground tracking-tighter mb-4">
        AI <span class="bg-clip-text text-transparent bg-gradient-to-r from-primary to-accent italic">Outfit</span> PK 对决
      </h1>
      <p class="text-muted-foreground text-xl max-w-2xl mx-auto font-medium">深度解析穿搭逻辑，为您在不同社交场景下的穿搭决策提供 AI 专业建议</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-12 gap-12 items-start">
      <!-- 左侧：输入控制区 -->
      <div class="lg:col-span-5 space-y-8 animate-slide-up">
        <div class="glass-card p-10 relative">
          <div class="absolute -top-4 -left-4 w-12 h-12 bg-primary rounded-2xl flex items-center justify-center text-white shadow-xl shadow-primary/30 z-10">
            <span class="text-xl font-black italic">PK</span>
          </div>

          <!-- 重置按钮 -->
          <div class="absolute top-6 right-8 z-10" v-if="imageAUrl || imageBUrl">
            <el-button link class="text-muted-foreground hover:text-primary transition-colors flex items-center gap-1 font-bold text-xs group/reset" @click="resetPK">
              <el-icon class="group-hover/reset:rotate-180 transition-transform duration-500"><RefreshRight /></el-icon> RESET
            </el-button>
          </div>

          <!-- 图片选择槽 -->
          <div class="flex items-center justify-between gap-8 relative">
            <div class="flex-1">
              <div class="text-xs font-black mb-4 uppercase tracking-widest text-muted-foreground/60">Contender A</div>
              <div class="pk-slot-container group" @click="openSelectionDialog('A')">
                <div v-if="imageAUrl" class="relative h-full w-full">
                  <img :src="imageAUrl" class="preview-img rounded-3xl shadow-2xl transition-transform duration-500 group-hover:scale-110" />
                  <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-all duration-300 rounded-3xl flex items-center justify-center backdrop-blur-sm">
                    <el-button type="primary" circle :icon="Plus" size="large"></el-button>
                  </div>
                </div>
                <div v-else class="pk-empty-slot">
                  <div class="w-16 h-16 rounded-2xl bg-primary/5 flex items-center justify-center mb-4 group-hover:bg-primary/10 transition-colors">
                    <el-icon size="32" class="text-primary/40"><Plus /></el-icon>
                  </div>
                  <div class="text-sm font-black text-muted-foreground">选择搭配 A</div>
                </div>
              </div>
            </div>

            <!-- VS 中间件 -->
            <div class="flex flex-col items-center gap-4 py-8">
              <div class="vs-badge">
                <span class="text-2xl font-black italic">VS</span>
              </div>
              <div class="w-px h-full bg-gradient-to-b from-transparent via-border to-transparent"></div>
            </div>

            <div class="flex-1">
              <div class="text-xs font-black mb-4 uppercase tracking-widest text-muted-foreground/60">Contender B</div>
              <div class="pk-slot-container group" @click="openSelectionDialog('B')">
                <div v-if="imageBUrl" class="relative h-full w-full">
                  <img :src="imageBUrl" class="preview-img rounded-3xl shadow-2xl transition-transform duration-500 group-hover:scale-110" />
                  <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-all duration-300 rounded-3xl flex items-center justify-center backdrop-blur-sm">
                    <el-button type="primary" circle :icon="Plus" size="large"></el-button>
                  </div>
                </div>
                <div v-else class="pk-empty-slot">
                  <div class="w-16 h-16 rounded-2xl bg-primary/5 flex items-center justify-center mb-4 group-hover:bg-primary/10 transition-colors">
                    <el-icon size="32" class="text-primary/40"><Plus /></el-icon>
                  </div>
                  <div class="text-sm font-black text-muted-foreground">选择搭配 B</div>
                </div>
              </div>
            </div>
          </div>

          <div class="mt-12 space-y-6">
            <div class="input-group group">
              <div class="flex items-center justify-between mb-3">
                <label class="text-xs font-black uppercase tracking-widest text-primary flex items-center gap-2">
                  <el-icon><ChatDotRound /></el-icon> 对比场景
                </label>
                <span class="text-[10px] text-muted-foreground bg-secondary/50 px-2 py-0.5 rounded">Required</span>
              </div>
              <el-input 
                v-model="scene" 
                placeholder="例如：学术演讲、校园路演、社团面试..." 
                class="premium-input"
                clearable
              ></el-input>
            </div>

            <el-button 
              type="primary" 
              size="large" 
              class="pk-start-btn"
              :loading="loading"
              :disabled="!imageAUrl || !imageBUrl"
              @click="startPK"
            >
              <template #loading>
                <div class="flex items-center gap-2">
                  <div class="scanning-dot"></div>
                  AI 正在深度分析中...
                </div>
              </template>
              <div class="flex items-center gap-3" v-if="!loading">
                <span>发起 AI 对决分析</span>
                <el-icon class="animate-bounce-x"><MagicStick /></el-icon>
              </div>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：结果展示区 -->
      <div class="lg:col-span-7 space-y-8">
        <transition name="el-fade-in-linear">
          <div v-if="pkResult" class="space-y-8">
            <!-- 获胜结论卡片 -->
            <div class="result-highlight-card" :class="pkResult.winner === 'A' ? 'winner-a' : 'winner-b'">
              <div class="flex items-start gap-6">
                <div class="winner-trophy shadow-inner">
                  <el-icon size="40"><Trophy /></el-icon>
                </div>
                <div class="flex-1 pt-1">
                  <div class="text-xs font-black uppercase tracking-widest mb-2 text-white/70">Analysis Conclusion</div>
                  <h3 class="text-3xl font-black text-white mb-4">
                    搭配 <span class="bg-white text-primary px-3 py-1 rounded-xl mx-1 shadow-lg">{{ pkResult.winner }}</span> 在当前场景胜出
                  </h3>
                  <div class="bg-black/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
                    <p class="text-lg leading-relaxed text-white/90 italic font-medium line-clamp-3">
                      “{{ pkResult.reason }}”
                    </p>
                  </div>
                </div>
              </div>
              <!-- 装饰背景 -->
              <div class="absolute top-0 right-0 w-32 h-32 bg-white/5 rounded-full -mr-16 -mt-16 blur-3xl"></div>
            </div>

            <!-- 数据可视化卡片 -->
            <div class="glass-card overflow-hidden">
              <div class="px-8 py-6 border-b border-white/10 flex items-center justify-between bg-primary/5">
                <h4 class="text-sm font-black uppercase tracking-widest flex items-center gap-2">
                  <el-icon class="text-primary"><MagicStick /></el-icon> 多维能力评估图谱
                </h4>
                <div class="flex items-center gap-4">
                  <div class="flex items-center gap-2 text-[10px] font-bold">
                    <span class="w-3 h-3 rounded-full bg-primary shadow-sm"></span> 搭配 A
                  </div>
                  <div class="flex items-center gap-2 text-[10px] font-bold">
                    <span class="w-3 h-3 rounded-full bg-accent shadow-sm"></span> 搭配 B
                  </div>
                </div>
              </div>
              <div class="p-8">
                <div ref="radarChartRef" style="width: 100%; height: 450px;"></div>
              </div>
            </div>

            <!-- 操作引导 -->
            <div class="flex justify-center pt-4">
              <el-button type="primary" plain class="rounded-2xl h-12 px-8 font-bold flex items-center gap-2 border-2 group/btn" @click="resetPK">
                重新开启一场对决 <el-icon class="group-hover/btn:translate-x-1 transition-transform"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>

          <div v-else class="glass-card h-full min-h-[640px] flex items-center justify-center flex-col animate-pulse-subtle p-12 text-center">
            <div class="w-32 h-32 rounded-[2.5rem] bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center mb-8 relative">
              <el-icon size="64" class="text-primary animate-float"><MagicStick /></el-icon>
              <div class="absolute inset-0 rounded-[2.5rem] border border-primary/20 animate-ping opacity-20"></div>
            </div>
            <h3 class="text-2xl font-black tracking-tighter mb-4 text-foreground/80 uppercase italic">Waiting for Duel</h3>
            <p class="text-muted-foreground max-w-xs mx-auto leading-relaxed font-medium">
              选择两套您心仪的穿搭并指定应用场景，点击按钮开始这场由 AI 驱动的深度美学对决。
            </p>
            <div class="mt-8 flex gap-2">
              <div class="w-2 h-2 rounded-full bg-primary/20"></div>
              <div class="w-2 h-2 rounded-full bg-primary/40 animate-pulse"></div>
              <div class="w-2 h-2 rounded-full bg-primary/20"></div>
            </div>
          </div>
        </transition>
      </div>
    </div>

    <!-- 资源选择弹窗 -->
    <el-dialog
      v-model="isSelectionDialogVisible"
      :title="`为搭配 ${targetSlot} 选择素材`"
      width="900px"
      append-to-body
      destroy-on-close
      class="selection-dialog-modern"
    >
      <div class="dialog-body">
        <el-tabs v-model="activeTab" class="premium-tabs">
          <el-tab-pane label="我的发布" name="posts">
            <template #label>
              <div class="flex items-center gap-2"><el-icon><Postcard /></el-icon><span>我的发布</span></div>
            </template>
          </el-tab-pane>
          <el-tab-pane label="我的收藏" name="favorites">
            <template #label>
              <div class="flex items-center gap-2"><el-icon><Collection /></el-icon><span>我的收藏</span></div>
            </template>
          </el-tab-pane>
          <el-tab-pane label="私人衣橱" name="wardrobe">
            <template #label>
              <div class="flex items-center gap-2"><el-icon><ShoppingBag /></el-icon><span>私人衣橱</span></div>
            </template>
          </el-tab-pane>
        </el-tabs>

        <div v-loading="selectionLoading" class="resource-grid-container mt-8 min-h-[450px]">
          <div v-if="getCurrentList.length === 0" class="flex flex-col items-center justify-center py-20 bg-secondary/10 rounded-3xl">
            <el-empty description="该分类下暂无可用素材" :image-size="120" />
          </div>
          <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6 max-h-[60vh] overflow-y-auto pr-2 custom-scrollbar">
            <div 
              v-for="(item, idx) in getCurrentList" 
              :key="idx" 
              class="resource-item group cursor-pointer"
              @click="handleSelectItem(item)"
            >
              <div class="relative aspect-[3/4] rounded-2xl overflow-hidden shadow-sm border border-transparent group-hover:border-primary/50 group-hover:shadow-xl group-hover:shadow-primary/10 transition-all duration-500">
                <el-image 
                  :src="getItemUrl(item)" 
                  class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" 
                  loading="lazy"
                />
                <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end justify-center pb-4">
                  <div class="bg-primary text-white p-2 rounded-full shadow-lg transform translate-y-4 group-hover:translate-y-0 transition-transform">
                    <el-icon size="20"><Check /></el-icon>
                  </div>
                </div>
              </div>
              <div class="mt-3 text-xs text-foreground font-bold truncate px-1 text-center group-hover:text-primary transition-colors">
                {{ item.title || item.categorySub || '未命名项目' }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.pk-page {
  background: radial-gradient(circle at top left, rgba(var(--primary-rgb), 0.03), transparent 40%),
              radial-gradient(circle at bottom right, rgba(var(--accent-rgb), 0.03), transparent 40%);
}

.glass-card {
  @apply bg-background/40 backdrop-blur-2xl border border-white/20 dark:border-white/5 rounded-[2.5rem] shadow-[0_32px_64px_-16px_rgba(0,0,0,0.1)];
}

.pk-slot-container {
  @apply w-full aspect-[3/4] cursor-pointer relative rounded-3xl overflow-hidden bg-secondary/20 border border-white/20;
}

.pk-empty-slot {
  @apply flex flex-col items-center justify-center h-full text-muted-foreground border-2 border-dashed border-border/60 transition-all duration-500 hover:bg-primary/5 hover:border-primary/30;
}

.preview-img {
  @apply w-full h-full object-cover;
}

.vs-badge {
  @apply w-14 h-14 rounded-2xl bg-background border-2 border-primary/20 flex items-center justify-center text-primary shadow-lg shadow-primary/10 relative z-10 animate-float;
}

.vs-badge::before {
  content: "";
  @apply absolute -inset-2 bg-primary/20 rounded-3xl blur-xl animate-pulse -z-10;
}

.premium-input :deep(.el-input__wrapper) {
  @apply rounded-2xl border-none shadow-none bg-secondary/50 h-14 px-6 text-lg transition-all duration-300;
}

.premium-input :deep(.el-input__wrapper.is-focus) {
  @apply bg-background shadow-[0_0_0_2px_rgba(var(--primary-rgb),0.2)];
}

.pk-start-btn {
  @apply w-full h-16 text-xl font-black rounded-2xl shadow-2xl shadow-primary/30 border-none relative overflow-hidden transition-all duration-500 active:scale-95;
  background: linear-gradient(135deg, rgb(var(--primary)) 0%, rgb(var(--primary-light)) 100%);
}

.pk-start-btn:not(:disabled):hover {
  @apply shadow-primary/50 -translate-y-1;
  filter: brightness(1.1);
}

.pk-start-btn:disabled {
  @apply opacity-50 grayscale cursor-not-allowed;
}

.result-highlight-card {
  @apply p-10 rounded-[2.5rem] relative overflow-hidden shadow-2xl;
}

.winner-a {
  background: linear-gradient(135deg, #409EFF 0%, #3a8ee6 100%);
}

.winner-b {
  background: linear-gradient(135deg, #F56C6C 0%, #ec5b5b 100%);
}

.winner-trophy {
  @apply w-20 h-20 rounded-3xl bg-white/20 backdrop-blur-md flex items-center justify-center text-white border border-white/30;
}

.animate-float { animation: float 6s ease-in-out infinite; }
.animate-bounce-x { animation: bounceX 1s infinite; }
.animate-pulse-subtle { animation: pulseSubtle 4s ease-in-out infinite; }

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0); }
  50% { transform: translateY(-15px) rotate(2deg); }
}

@keyframes bounceX {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(5px); }
}

@keyframes pulseSubtle {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(0.995); }
}

.scanning-dot {
  @apply w-2 h-2 rounded-full bg-white animate-ping;
}

/* 弹窗样式 */
.selection-dialog-modern :deep(.el-dialog) {
  @apply rounded-[3rem] overflow-hidden bg-background/80 backdrop-blur-3xl border border-white/20;
}

.selection-dialog-modern :deep(.el-dialog__header) {
  @apply p-8 pb-4;
}

.selection-dialog-modern :deep(.el-dialog__title) {
  @apply text-2xl font-black italic tracking-tighter;
}

.premium-tabs :deep(.el-tabs__nav-wrap::after) {
  @apply hidden;
}

.premium-tabs :deep(.el-tabs__active-bar) {
  @apply h-1 rounded-full bg-primary;
}

.premium-tabs :deep(.el-tabs__item) {
  @apply text-lg font-bold py-6 h-auto text-muted-foreground transition-all duration-300;
}

.premium-tabs :deep(.el-tabs__item.is-active) {
  @apply text-primary scale-110;
}

.custom-scrollbar::-webkit-scrollbar {
  @apply w-1.5;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  @apply bg-primary/20 rounded-full hover:bg-primary/40 transition-colors;
}

.animate-fade-in { animation: fadeIn 0.8s cubic-bezier(0.16, 1, 0.3, 1); }
.animate-slide-up { animation: slideUp 1s cubic-bezier(0.16, 1, 0.3, 1) forwards; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideUp { from { opacity: 0; transform: translateY(40px); } to { opacity: 1; transform: translateY(0); } }
</style>
