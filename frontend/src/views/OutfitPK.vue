<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { pkOutfits } from '@/api/ai'
import { getWardrobeList } from '@/api/wardrobe'
import { getMyOutfits, getFavoriteOutfits } from '@/api/outfit'
import { ElMessage } from 'element-plus'
import { MagicStick, ChatDotRound, Trophy, Plus, Check, Collection, Postcard, ShoppingBag } from '@element-plus/icons-vue'

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

  const option = {
    color: ['#409EFF', '#F56C6C'],
    legend: { data: ['搭配 A', '搭配 B'], bottom: 0 },
    radar: {
      indicator: radarData.dimensions.map((dim: string) => ({ name: dim, max: 100 })),
      radius: '60%',
      splitArea: { areaStyle: { color: ['#f8f9fa', '#fff'] } }
    },
    series: [
      {
        type: 'radar',
        data: [
          { value: radarData.scoresA, name: '搭配 A', areaStyle: { opacity: 0.3 } },
          { value: radarData.scoresB, name: '搭配 B', areaStyle: { opacity: 0.3 } }
        ]
      }
    ]
  }
  myChart.setOption(option)
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
  <div class="pk-page max-w-7xl mx-auto px-4 py-12">
    <div class="text-center mb-12 animate-fade-in">
      <h1 class="text-4xl font-extrabold text-foreground tracking-tight mb-4 flex items-center justify-center gap-3">
        <el-icon class="text-primary"><MagicStick /></el-icon>
        AI <span class="text-primary italic">Outfit</span> PK 对决
      </h1>
      <p class="text-muted-foreground text-lg">从您的发布、收藏或衣柜中选取搭配，让 AI 专家在特定场景下深度对决</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-10">
      <!-- 左侧：输入控制区 -->
      <div class="glass-card p-8 space-y-8 animate-slide-up">
        <!-- 图片选择槽 -->
        <div class="flex items-center justify-between gap-6">
          <div class="flex-1 text-center">
            <div class="text-sm font-black mb-4 uppercase text-muted-foreground">搭配 A</div>
            <div class="pk-slot-container" @click="openSelectionDialog('A')">
              <img v-if="imageAUrl" :src="imageAUrl" class="preview-img rounded-2xl shadow-md border-2 border-primary/20" />
              <div v-else class="pk-empty-slot">
                <el-icon size="30"><Plus /></el-icon>
                <div class="text-xs mt-2 font-bold">选择搭配 A</div>
              </div>
            </div>
          </div>

          <div class="text-3xl font-black italic text-muted-foreground/30 pt-8">VS</div>

          <div class="flex-1 text-center">
            <div class="text-sm font-black mb-4 uppercase text-muted-foreground">搭配 B</div>
            <div class="pk-slot-container" @click="openSelectionDialog('B')">
              <img v-if="imageBUrl" :src="imageBUrl" class="preview-img rounded-2xl shadow-md border-2 border-primary/20" />
              <div v-else class="pk-empty-slot">
                <el-icon size="30"><Plus /></el-icon>
                <div class="text-xs mt-2 font-bold">选择搭配 B</div>
              </div>
            </div>
          </div>
        </div>

        <div class="space-y-4 pt-4">
          <div class="input-group">
            <label class="block text-xs font-black uppercase text-primary mb-2">对比场景</label>
            <el-input v-model="scene" placeholder="例如：校园面试、社团晚会..." clearable>
              <template #prefix><el-icon><ChatDotRound /></el-icon></template>
            </el-input>
          </div>
          <el-button 
            type="primary" 
            size="large" 
            class="w-full h-14 text-lg font-bold rounded-2xl shadow-lg shadow-primary/20"
            :loading="loading"
            :disabled="!imageAUrl || !imageBUrl"
            @click="startPK"
          >
            开始 AI 对决分析
          </el-button>
        </div>
      </div>

      <!-- 右侧：结果展示区 -->
      <div class="space-y-8">
        <template v-if="pkResult">
          <div class="glass-card p-8 border-2 border-primary/20 bg-primary/5 animate-fade-in">
            <div class="flex items-center gap-4 mb-6">
              <el-icon size="32" class="text-primary"><Trophy /></el-icon>
              <h3 class="text-2xl font-black">AI 认定结论：搭配 {{ pkResult.winner }} 胜出</h3>
            </div>
            <p class="text-lg leading-relaxed text-foreground italic font-medium">“{{ pkResult.reason }}”</p>
          </div>

          <div class="glass-card p-8 min-h-[400px] animate-slide-up">
            <div ref="radarChartRef" style="width: 100%; height: 400px;"></div>
          </div>
        </template>
        <div v-else class="glass-card h-full min-h-[500px] flex-center flex-col text-muted-foreground opacity-30 select-none border-dashed border-2">
          <el-icon size="64" class="mb-4"><MagicStick /></el-icon>
          <p class="text-xl font-bold italic uppercase tracking-tighter">Waiting for the Duel Analysis</p>
        </div>
      </div>
    </div>

    <!-- 资源选择弹窗 -->
    <el-dialog
      v-model="isSelectionDialogVisible"
      :title="`为搭配 ${targetSlot} 选择素材`"
      width="850px"
      append-to-body
      destroy-on-close
      class="selection-dialog"
    >
      <div class="dialog-body pt-2">
        <el-tabs v-model="activeTab" class="custom-tabs">
          <el-tab-pane label="我的发布" name="posts">
            <template #label>
              <span class="flex items-center gap-2"><el-icon><Postcard /></el-icon>我的发布</span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="我的收藏" name="favorites">
            <template #label>
              <span class="flex items-center gap-2"><el-icon><Collection /></el-icon>我的收藏</span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="私人衣橱" name="wardrobe">
            <template #label>
              <span class="flex items-center gap-2"><el-icon><ShoppingBag /></el-icon>私人衣橱</span>
            </template>
          </el-tab-pane>
        </el-tabs>

        <div v-loading="selectionLoading" class="resource-grid-container mt-4 min-h-[400px]">
          <div v-if="getCurrentList.length === 0" class="flex flex-col items-center justify-center pt-20">
            <el-empty description="该分类下暂无可用素材" />
          </div>
          <div v-else class="grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 h-[60vh] overflow-y-auto pr-2">
            <div 
              v-for="(item, idx) in getCurrentList" 
              :key="idx" 
              class="resource-item group relative cursor-pointer"
              @click="handleSelectItem(item)"
            >
              <el-image 
                :src="getItemUrl(item)" 
                class="w-full aspect-[3/4] rounded-xl object-cover border-2 border-transparent group-hover:border-primary transition-all" 
              />
              <div class="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity rounded-xl flex items-center justify-center">
                <el-icon size="24" class="text-white"><Check /></el-icon>
              </div>
              <div class="mt-1 text-[10px] text-muted-foreground truncate px-1 text-center">
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
.glass-card {
  @apply bg-background/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] shadow-[0_8px_32px_rgba(0,0,0,0.05)];
}

.pk-slot-container {
  @apply w-full aspect-[3/4] cursor-pointer transition-all hover:scale-[1.02];
}

.pk-empty-slot {
  @apply border-2 border-dashed border-border rounded-2xl flex flex-col items-center justify-center h-full bg-secondary/10 text-muted-foreground hover:border-primary/50 transition-colors;
}

.preview-img {
  @apply w-full h-full object-cover;
}

.animate-fade-in { animation: fadeIn 0.6s ease-out; }
.animate-slide-up { animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideUp { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }

:deep(.el-input__wrapper) { @apply rounded-xl border-none shadow-none bg-secondary/30 h-12 transition-all; }

.custom-tabs :deep(.el-tabs__header) {
  @apply mb-0;
}

.selection-dialog :deep(.el-dialog__body) {
  @apply p-6;
}

.flex-center {
  @apply flex items-center justify-center;
}
</style>
