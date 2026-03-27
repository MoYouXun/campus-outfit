<script setup lang="ts">
import { ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { pkOutfits } from '@/api/ai'
import { uploadWardrobeItem } from '@/api/wardrobe'
import { ElMessage } from 'element-plus'
import { MagicStick, ChatDotRound, Trophy, Plus, Loading } from '@element-plus/icons-vue'

// 响应式变量
const imageAUrl = ref('')
const imageBUrl = ref('')
const scene = ref('校园路演/学术演讲')
const loading = ref(false)
const uploadingA = ref(false)
const uploadingB = ref(false)
const pkResult = ref<any>(null)
const radarChartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

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
 * 处理图片 A 上传
 */
const handleUploadA = async (options: any) => {
  uploadingA.value = true
  try {
    const res: any = await uploadWardrobeItem(options.file)
    imageAUrl.value = res.originalImageUrl
    ElMessage.success('搭配 A 图片上传成功')
  } catch (e: any) {
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploadingA.value = false
  }
}

/**
 * 处理图片 B 上传
 */
const handleUploadB = async (options: any) => {
  uploadingB.value = true
  try {
    const res: any = await uploadWardrobeItem(options.file)
    imageBUrl.value = res.originalImageUrl
    ElMessage.success('搭配 B 图片上传成功')
  } catch (e: any) {
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploadingB.value = false
  }
}

/**
 * 开始 PK 逻辑
 */
const startPK = async () => {
  if (!imageAUrl.value || !imageBUrl.value || !scene.value) {
    ElMessage.warning('请确保图片已上传并填写场景')
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
      <p class="text-muted-foreground text-lg">上传两套搭配图片，让 AI 专家为您在特定场景下进行深度对决分析</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-10">
      <!-- 左侧：上传与操作区 -->
      <div class="glass-card p-8 space-y-8 animate-slide-up">
        <!-- 图片上传槽 -->
        <div class="flex items-center justify-between gap-6">
          <div class="flex-1 text-center">
            <div class="text-sm font-black mb-4 uppercase text-muted-foreground">搭配 A</div>
            <el-upload
              class="pk-uploader"
              action="#"
              :auto-upload="true"
              :http-request="handleUploadA"
              :show-file-list="false"
              v-loading="uploadingA"
            >
              <img v-if="imageAUrl" :src="imageAUrl" class="preview-img" />
              <el-icon v-else class="uploader-icon"><Plus /></el-icon>
            </el-upload>
          </div>

          <div class="text-3xl font-black italic text-muted-foreground/30 pt-8">VS</div>

          <div class="flex-1 text-center">
            <div class="text-sm font-black mb-4 uppercase text-muted-foreground">搭配 B</div>
            <el-upload
              class="pk-uploader"
              action="#"
              :auto-upload="true"
              :http-request="handleUploadB"
              :show-file-list="false"
              v-loading="uploadingB"
            >
              <img v-if="imageBUrl" :src="imageBUrl" class="preview-img" />
              <el-icon v-else class="uploader-icon"><Plus /></el-icon>
            </el-upload>
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
            class="w-full h-14 text-lg font-bold rounded-2xl"
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
              <h3 class="text-2xl font-black">AI 认定结果：方案 {{ pkResult.winner }} 胜出</h3>
            </div>
            <p class="text-lg leading-relaxed text-foreground italic font-medium">“{{ pkResult.reason }}”</p>
          </div>

          <div class="glass-card p-8 min-h-[400px] animate-slide-up">
            <div ref="radarChartRef" style="width: 100%; height: 400px;"></div>
          </div>
        </template>
        <div v-else class="glass-card h-full min-h-[500px] flex-center flex-col text-muted-foreground opacity-30 select-none border-dashed border-2">
          <el-icon size="64" class="mb-4"><MagicStick /></el-icon>
          <p class="text-xl font-bold italic">WITING FOR THE DUEL</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-card {
  @apply bg-background/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] shadow-[0_8px_32px_rgba(0,0,0,0.05)];
}

.pk-uploader :deep(.el-upload) {
  @apply border-2 border-dashed border-border rounded-2xl cursor-pointer relative overflow-hidden transition-all hover:border-primary/50 bg-secondary/20 flex items-center justify-center w-full aspect-[3/4];
}

.preview-img {
  @apply w-full h-full object-cover;
}

.uploader-icon {
  @apply text-3xl text-muted-foreground;
}

.animate-fade-in { animation: fadeIn 0.6s ease-out; }
.animate-slide-up { animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideUp { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }

:deep(.el-input__wrapper) { @apply rounded-xl border-none shadow-none bg-secondary/30 h-12 transition-all; }
</style>
