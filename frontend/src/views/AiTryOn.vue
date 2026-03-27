<script setup lang="ts">
import { ref } from 'vue'
import { Plus, MagicStick, Loading as IconLoading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadWardrobeItem } from '@/api/wardrobe'
import { aiTryOn } from '@/api/ai'

// 响应式状态
const humanImageUrl = ref('')
const garmentImageUrl = ref('')
const resultImageUrl = ref('')
const isGenerating = ref(false)
const humanUploading = ref(false)
const garmentUploading = ref(false)

/**
 * 处理人像图片上传
 */
const handleHumanUpload = async (options: any) => {
  humanUploading.value = true
  try {
    const res: any = await uploadWardrobeItem(options.file)
    humanImageUrl.value = res.originalImageUrl
    ElMessage.success('人像图片上传成功')
  } catch (e: any) {
    ElMessage.error('人像上传失败：' + (e.message || '未知错误'))
  } finally {
    humanUploading.value = false
  }
}

/**
 * 处理服装图片上传
 */
const handleGarmentUpload = async (options: any) => {
  garmentUploading.value = true
  try {
    const res: any = await uploadWardrobeItem(options.file)
    garmentImageUrl.value = res.originalImageUrl
    ElMessage.success('服装图片上传成功')
  } catch (e: any) {
    ElMessage.error('服装上传失败：' + (e.message || '未知错误'))
  } finally {
    garmentUploading.value = false
  }
}

/**
 * 执行 AI 试衣
 */
const handleTryOn = async () => {
  if (!humanImageUrl.value || !garmentImageUrl.value) {
    ElMessage.warning('请先上传人像照和服装照后再尝试一键试穿')
    return
  }

  isGenerating.value = true
  resultImageUrl.value = '' // 清除旧结果

  try {
    const res: any = await aiTryOn({
      humanImageUrl: humanImageUrl.value,
      garmentImageUrl: garmentImageUrl.value,
      category: 'upper_body' // 默认上装，可扩展
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
      <p class="text-muted-foreground text-lg">上传您的人像与心仪服装，让 AI 为您呈现最真实的试穿效果</p>
    </div>

    <el-row :gutter="40">
      <!-- 左侧：输入控制区 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="8">
        <div class="glass-card p-6 space-y-8 animate-slide-up">
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
                <span class="text-xs text-muted-foreground mt-2">点击选择人像</span>
              </div>
            </el-upload>
          </div>

          <div class="upload-group">
            <h3 class="text-sm font-black uppercase text-primary mb-4 flex items-center gap-2">
              <div class="w-1.5 h-4 bg-primary rounded-full"></div>
              第 2 步：上传衣服照
            </h3>
            <el-upload
              class="tryon-uploader"
              action="#"
              :show-file-list="false"
              :auto-upload="true"
              :http-request="handleGarmentUpload"
              v-loading="garmentUploading"
            >
              <img v-if="garmentImageUrl" :src="garmentImageUrl" class="preview-img" />
              <div v-else class="flex flex-col items-center">
                <el-icon class="uploader-icon"><Plus /></el-icon>
                <span class="text-xs text-muted-foreground mt-2">点击选择服装</span>
              </div>
            </el-upload>
          </div>

          <el-button
            type="primary"
            class="tryon-btn w-full h-14 text-lg font-bold rounded-2xl shadow-lg shadow-primary/30"
            :loading="isGenerating"
            :disabled="!humanImageUrl || !garmentImageUrl"
            @click="handleTryOn"
          >
            一键魔法试穿 ✨
          </el-button>
        </div>
      </el-col>

      <!-- 右侧：生成结果区 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="16">
        <el-card class="result-card glass-card relative overflow-hidden animate-slide-up-slow" shadow="hover">
          <div v-if="isGenerating" class="loading-overlay flex flex-col items-center justify-center p-20">
            <div class="pulse-animation mb-6">
              <el-icon size="64" class="text-primary animate-spin"><IconLoading /></el-icon>
            </div>
            <h3 class="text-2xl font-bold mb-2">AI 魔法合成中...</h3>
            <p class="text-muted-foreground animate-pulse text-center">正在处理面料纹理与光影融合，预计需要 10-20 秒，请稍候</p>
          </div>

          <div v-else-if="resultImageUrl" class="result-display h-full min-h-[600px] flex items-center justify-center">
            <el-image
              :src="resultImageUrl"
              class="w-full h-full object-contain rounded-xl"
              :preview-src-list="[resultImageUrl]"
              fit="contain"
            />
            <div class="absolute bottom-6 right-6">
              <el-button type="success" round size="large" class="shadow-xl" icon="Download">保存穿衣效果</el-button>
            </div>
          </div>

          <div v-else class="empty-placeholder h-full min-h-[600px] flex-center flex-col text-muted-foreground p-12 select-none border-2 border-dashed border-border/40 rounded-[2rem]">
            <el-icon size="80" class="mb-6 opacity-20"><MagicStick /></el-icon>
            <p class="text-xl font-bold italic opacity-30 tracking-widest uppercase">Select images to start magic</p>
            <p class="mt-4 text-sm opacity-50">您的试穿结果将在此处展示</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
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
</style>
