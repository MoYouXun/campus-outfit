<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus, MagicStick, Loading as IconLoading, Refresh, Close, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getWardrobeList } from '@/api/wardrobe'
import { uploadPortrait } from '@/api/image'
import { aiTryOn } from '@/api/ai'

// 响应式状态
const humanImageUrl = ref('')
const upperGarmentUrl = ref('')
const lowerGarmentUrl = ref('')
const resultImageUrl = ref('')

const isGenerating = ref(false)
const humanUploading = ref(false)

// 衣柜选择弹窗相关
const isWardrobeDialogVisible = ref(false)
const wardrobeLoading = ref(false)
const wardrobeItems = ref<any[]>([])
const selectionType = ref<'upper' | 'lower'>('upper')

/**
 * 处理人像图片上传
 */
const handleHumanUpload = async (options: any) => {
  humanUploading.value = true
  try {
    const res: any = await uploadPortrait(options.file)
    humanImageUrl.value = res.url
    ElMessage.success('人像图片预核通过，上传成功')
  } catch (e: any) {
    ElMessage.error('人像上传失败：' + (e.message || '未知错误'))
  } finally {
    humanUploading.value = false
  }
}

/**
 * 打开衣柜选择弹窗
 */
const openWardrobeDialog = async (type: 'upper' | 'lower') => {
  selectionType.value = type
  isWardrobeDialogVisible.value = true
  
  if (wardrobeItems.value.length === 0) {
    fetchWardrobe()
  }
}

/**
 * 获取衣柜列表
 */
const fetchWardrobe = async () => {
  wardrobeLoading.value = true
  try {
    const res: any = await getWardrobeList()
    wardrobeItems.value = res
  } catch (e: any) {
    ElMessage.error('获取衣柜列表失败')
  } finally {
    wardrobeLoading.value = false
  }
}

/**
 * 过滤后的衣柜列表
 */
const filteredWardrobeItems = computed(() => {
  const targetCategory = selectionType.value === 'upper' ? '上装' : '下装'
  return wardrobeItems.value.filter(item => item.categoryMain === targetCategory)
})

/**
 * 选择单品
 */
const selectGarment = (item: any) => {
  if (selectionType.value === 'upper') {
    upperGarmentUrl.value = item.originalImageUrl
  } else {
    lowerGarmentUrl.value = item.originalImageUrl
  }
  isWardrobeDialogVisible.value = false
  ElMessage.success(`已选中${selectionType.value === 'upper' ? '上装' : '下装'}`)
}

/**
 * 移除已选单品
 */
const removeGarment = (type: 'upper' | 'lower') => {
  if (type === 'upper') {
    upperGarmentUrl.value = ''
  } else {
    lowerGarmentUrl.value = ''
  }
}

/**
 * 执行 AI 试衣
 */
const handleTryOn = async () => {
  if (!humanImageUrl.value) {
    ElMessage.warning('请先上传人像照')
    return
  }
  
  if (!upperGarmentUrl.value && !lowerGarmentUrl.value) {
    ElMessage.warning('请至少选择一件上装或下装进行试穿')
    return
  }

  isGenerating.value = true
  resultImageUrl.value = ''

  try {
    const res: any = await aiTryOn({
      humanImageUrl: humanImageUrl.value,
      upperGarmentUrl: upperGarmentUrl.value || undefined,
      lowerGarmentUrl: lowerGarmentUrl.value || undefined
    })
    resultImageUrl.value = res.resultImageUrl
    ElMessage.success('AI 魔法试穿成功！')
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e.message || '系统异常'))
  } finally {
    isGenerating.value = false
  }
}
</script>

<template>
  <div class="tryon-container max-w-7xl mx-auto px-4 py-12">
    <!-- 头部标题 -->
    <div class="header-section text-center mb-12 animate-fade-in">
      <h1 class="text-4xl font-black mb-4 flex items-center justify-center gap-3">
        <span class="text-primary italic">AI</span> 魔法试衣间 ✨
      </h1>
      <p class="text-muted-foreground text-lg">组合您的心仪上装与下装，让 AI 为您呈现全身试穿效果</p>
    </div>

    <el-row :gutter="40">
      <!-- 左侧：输入控制区 -->
      <el-col :xs="24" :sm="24" :md="10" :lg="9">
        <div class="glass-card p-6 space-y-8 animate-slide-up">
          <!-- 步骤1：人像 -->
          <div class="upload-group">
            <h3 class="text-sm font-black uppercase text-primary mb-4 flex items-center gap-2">
              <div class="w-1.5 h-4 bg-primary rounded-full"></div>
              第 1 步：上传人像照
            </h3>
            <el-upload
              class="tryon-uploader"
              action="#"
              :show-file-list="false"
              :auto-upload="true"
              :http-request="handleHumanUpload"
              v-loading="humanUploading"
            >
              <img v-if="humanImageUrl" :src="humanImageUrl" class="preview-img" />
              <div v-else class="flex flex-col items-center">
                <el-icon class="uploader-icon"><Plus /></el-icon>
                <span class="text-xs text-muted-foreground mt-2">点击上传人像底图</span>
              </div>
            </el-upload>
          </div>

          <!-- 步骤2：服装选择 -->
          <div class="upload-group">
            <h3 class="text-sm font-black uppercase text-primary mb-4 flex items-center gap-2">
              <div class="w-1.5 h-4 bg-primary rounded-full"></div>
              第 2 步：选择试穿服装
            </h3>
            
            <div class="grid grid-cols-2 gap-4">
              <!-- 上装位 -->
              <div class="garment-slot-container">
                <div v-if="upperGarmentUrl" class="selected-garment relative group">
                  <el-image :src="upperGarmentUrl" class="w-full aspect-[3/4] rounded-xl object-cover border-2 border-primary" />
                  <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center gap-2 rounded-xl">
                    <el-button type="primary" size="small" :icon="Refresh" circle @click="openWardrobeDialog('upper')" />
                    <el-button type="danger" size="small" :icon="Close" circle @click="removeGarment('upper')" />
                  </div>
                  <div class="absolute bottom-2 left-2 bg-primary text-white text-[10px] px-2 py-0.5 rounded-md">已选上装</div>
                </div>
                <div v-else class="garment-empty-slot" @click="openWardrobeDialog('upper')">
                  <el-icon size="24"><Plus /></el-icon>
                  <span class="text-[10px] mt-1 font-bold">选择上装</span>
                </div>
              </div>

              <!-- 下装位 -->
              <div class="garment-slot-container">
                <div v-if="lowerGarmentUrl" class="selected-garment relative group">
                  <el-image :src="lowerGarmentUrl" class="w-full aspect-[3/4] rounded-xl object-cover border-2 border-primary" />
                  <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center gap-2 rounded-xl">
                    <el-button type="primary" size="small" :icon="Refresh" circle @click="openWardrobeDialog('lower')" />
                    <el-button type="danger" size="small" :icon="Close" circle @click="removeGarment('lower')" />
                  </div>
                  <div class="absolute bottom-2 left-2 bg-primary text-white text-[10px] px-2 py-0.5 rounded-md">已选下装</div>
                </div>
                <div v-else class="garment-empty-slot" @click="openWardrobeDialog('lower')">
                  <el-icon size="24"><Plus /></el-icon>
                  <span class="text-[10px] mt-1 font-bold">选择下装</span>
                </div>
              </div>
            </div>
          </div>

          <el-button
            type="primary"
            class="tryon-btn w-full h-14 text-lg font-bold rounded-2xl shadow-lg shadow-primary/30"
            :loading="isGenerating"
            :disabled="!humanImageUrl || (!upperGarmentUrl && !lowerGarmentUrl)"
            @click="handleTryOn"
          >
            一键魔法试穿 ✨
          </el-button>
        </div>
      </el-col>

      <!-- 右侧：生成结果区 -->
      <el-col :xs="24" :sm="24" :md="14" :lg="15">
        <el-card class="result-card glass-card relative overflow-hidden animate-slide-up-slow" shadow="hover">
          <div v-if="isGenerating" class="loading-overlay flex flex-col items-center justify-center p-20">
            <div class="pulse-animation mb-6">
              <el-icon size="64" class="text-primary animate-spin"><IconLoading /></el-icon>
            </div>
            <h3 class="text-2xl font-bold mb-2">AI 正在编织时尚...</h3>
            <p class="text-muted-foreground animate-pulse text-center max-w-sm">正在深度融合面料褶皱、人体轮廓与环境光影，预计需要 1-2 分钟，请耐心等待这份惊艳</p>
          </div>

          <div v-else-if="resultImageUrl" class="result-display h-full min-h-[600px] flex items-center justify-center relative">
            <el-image
              :src="resultImageUrl"
              class="w-full h-full object-contain rounded-xl"
              :preview-src-list="[resultImageUrl]"
              fit="contain"
            />
            <div class="absolute bottom-6 left-1/2 -translate-x-1/2 flex gap-4">
              <el-button type="success" round size="large" class="shadow-xl px-8" icon="Download">保存试穿效果</el-button>
              <el-button type="info" round size="large" class="shadow-xl px-8 bg-white/20 backdrop-blur-md border-white/30" @click="resultImageUrl = ''">重新配置</el-button>
            </div>
          </div>

          <div v-else class="empty-placeholder h-full min-h-[600px] flex-center flex-col text-muted-foreground p-12 select-none border-2 border-dashed border-border/40 rounded-[2rem]">
            <el-icon size="80" class="mb-6 opacity-20"><MagicStick /></el-icon>
            <p class="text-xl font-bold italic opacity-30 tracking-widest uppercase">Select garments for full body try-on</p>
            <p class="mt-4 text-sm opacity-50">配置完成后，您的 AI 生成效果将在此展示</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 衣柜选择弹窗 -->
    <el-dialog
      v-model="isWardrobeDialogVisible"
      :title="`从衣柜选择${selectionType === 'upper' ? '上装' : '下装'}`"
      width="800px"
      class="wardrobe-select-dialog"
      append-to-body
      destroy-on-close
    >
      <div v-loading="wardrobeLoading" class="min-h-[400px]">
        <div v-if="filteredWardrobeItems.length === 0" class="flex flex-col items-center justify-center py-20">
          <el-empty :description="`您的衣柜里还没有${selectionType === 'upper' ? '上装' : '下装'}哦，去个人中心上传吧！`" />
        </div>
        <div v-else class="grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 max-h-[60vh] overflow-y-auto pr-2">
          <div 
            v-for="item in filteredWardrobeItems" 
            :key="item.id" 
            class="wardrobe-pick-item group cursor-pointer relative"
            @click="selectGarment(item)"
          >
            <el-image :src="item.originalImageUrl" class="w-full aspect-[3/4] rounded-xl object-cover border-2 border-transparent group-hover:border-primary transition-all" />
            <div class="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
              <el-icon class="bg-primary text-white p-1 rounded-full"><Check /></el-icon>
            </div>
            <div class="mt-1 text-[10px] text-center text-muted-foreground truncate">{{ item.categorySub }}</div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.glass-card {
  @apply bg-background/60 backdrop-blur-xl border border-border/50 rounded-[2.5rem] shadow-[0_8px_32px_rgba(0,0,0,0.05)] transition-all;
}

.tryon-uploader :deep(.el-upload) {
  @apply border-2 border-dashed border-border rounded-2xl cursor-pointer relative overflow-hidden transition-all hover:border-primary/50 bg-secondary/10 flex items-center justify-center w-full aspect-[4/3];
}

.preview-img {
  @apply w-full h-full object-cover;
}

.uploader-icon {
  @apply text-3xl text-muted-foreground;
}

.garment-empty-slot {
  @apply border-2 border-dashed border-border rounded-xl aspect-[3/4] flex flex-col items-center justify-center cursor-pointer transition-all hover:border-primary/50 hover:bg-primary/5 bg-secondary/5 text-muted-foreground;
}

.result-card {
  @apply min-h-[650px] border-none flex flex-col justify-center;
}

.loading-overlay {
  @apply flex-1;
}

.pulse-animation {
  @apply relative flex-center;
}

.pulse-animation::before {
  content: '';
  @apply absolute w-24 h-24 bg-primary/20 rounded-full animate-ping;
}

.animate-fade-in { animation: fadeIn 0.6s ease-out; }
.animate-slide-up { animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards; }
.animate-slide-up-slow { animation: slideUp 1s cubic-bezier(0.16, 1, 0.3, 1) 0.2s forwards; opacity: 0; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideUp { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }

.flex-center {
  @apply flex items-center justify-center;
}

.wardrobe-select-dialog :deep(.el-dialog__body) {
  @apply pt-2 pb-6 px-6;
}
</style>
