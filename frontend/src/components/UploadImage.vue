<script setup lang="ts">
import { ref } from 'vue'
import { UploadFilled, Refresh, Camera, Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import Compressor from 'compressorjs'
import type { UploadRequestOptions } from 'element-plus'

// 定义组件属性
const props = defineProps({
  // 样式变体：'default' 默认，'compact' 紧凑
  variant: {
    type: String,
    default: 'default'
  },
  // 压缩的最大宽度
  maxWidth: {
    type: Number,
    default: 1200
  }
})

// 定义事件
const emit = defineEmits(['upload-success'])
// 上传状态
const isUploading = ref(false)
// 预览图链接
const previewImage = ref<string | null>(null)

// 工具：File 转 Base64
const fileToBase64 = (file: File | Blob): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = error => reject(error)
  })
}

// 手动上传与压缩逻辑
const customUpload = (options: UploadRequestOptions) => {
  const file = options.file
  isUploading.value = true

  // 使用 Compressor.js 进行客户端图片压缩
  new Compressor(file, {
    quality: 0.7, // 保持较好画质
    maxWidth: props.maxWidth,
    success: async (compressedResult: File | Blob) => {
      try {
        const compressedSize = (compressedResult.size / 1024 / 1024).toFixed(2)
        ElMessage.success(`智能压缩完成 (${compressedSize}MB)`)
        
        // 生成预览图
        previewImage.value = await fileToBase64(compressedResult)
        
        // 确保是 File 类型
        let finalFile: File
        if (compressedResult instanceof File) {
          finalFile = compressedResult
        } else {
          finalFile = new File([compressedResult], options.file.name, {
            type: compressedResult.type || 'image/jpeg'
          })
        }
        
        // 模拟 AI 提取的等待效果，增强体验
        setTimeout(() => {
          isUploading.value = false
          emit('upload-success', {
            originalFile: file,
            compressedFile: finalFile,
            base64Data: previewImage.value
          })
          options.onSuccess?.({ message: 'Success' })
        }, 1200)

      } catch (err) {
        options.onError?.(err as Error)
        isUploading.value = false
        ElMessage.error('图片读取失败')
      }
    },
    error: (err: Error) => {
      ElMessage.error(`压缩失败：${err.message}`)
      isUploading.value = false
      options.onError?.(err)
    }
  })
}

// 移除当前图片
const removeImage = () => {
  previewImage.value = null
}
</script>

<template>
  <!-- 默认穿搭提取模式 -->
  <div v-if="variant === 'default'" class="upload-container">
    <div class="upload-box" :class="{ 'is-uploading': isUploading }">
      <!-- 装饰背景 -->
      <div class="glow-effect glow-1"></div>
      <div class="glow-effect glow-2"></div>
      
      <el-upload
        class="ai-upload-dragger"
        drag
        action="#"
        :http-request="customUpload"
        :show-file-list="false"
        accept="image/*"
        :disabled="isUploading"
      >
        <!-- 未上传状态 -->
        <template v-if="!previewImage">
          <div class="empty-state">
            <div class="icon-wrapper">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="pulse-ring"></div>
            </div>
            <h3 class="title">智能穿搭提取</h3>
            <p class="subtitle">拖拽校园穿搭照片至此，或 <span>点击浏览</span></p>
            <div class="feature-tags">
              <span class="tag"><el-icon><Picture /></el-icon> 自动扣图</span>
              <span class="tag"><el-icon><Camera /></el-icon> 风格识别</span>
            </div>
          </div>
        </template>
        
        <!-- 已上传预览状态 -->
        <template v-else>
          <div class="preview-state">
            <img :src="previewImage" alt="穿搭预览" class="preview-img" />
            <div class="scan-line" v-if="isUploading"></div>
            
            <div class="preview-overlay" v-if="!isUploading">
              <el-button type="danger" size="small" circle :icon="Refresh" @click.stop="removeImage" class="action-btn" />
              <span class="overlay-text">点击或拖拽更换图片</span>
            </div>
          </div>
        </template>
        
        <!-- 底部提示 -->
        <template #tip>
          <div class="upload-tip-text">
            支持 JPG/PNG 格式，AI 引擎将自动分析单品与配色
          </div>
        </template>
      </el-upload>
      
      <!-- 进度指示器 -->
      <Transition name="fade">
        <div v-if="isUploading" class="processing-indicator">
          <div class="progress-bar-wrapper">
            <div class="progress-bar-fill"></div>
          </div>
          <span class="processing-text">AI 正在深度解析穿搭基因...</span>
        </div>
      </Transition>
    </div>
  </div>

  <!-- 紧凑模式（如头像上传） -->
  <div v-else class="compact-upload">
    <el-upload
      action="#"
      :http-request="customUpload"
      :show-file-list="false"
      accept="image/*"
      :disabled="isUploading"
    >
      <slot>
        <div class="compact-btn" :class="{ 'loading': isUploading }">
          <el-icon><Camera /></el-icon>
        </div>
      </slot>
    </el-upload>
  </div>
</template>

<style scoped>
.upload-container {
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
}

.upload-box {
  position: relative;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 24px;
  padding: 20px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.05);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.dark .upload-box {
  background: rgba(30, 30, 35, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
}

.upload-box:hover {
  transform: translateY(-2px);
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.08);
  border-color: rgba(var(--el-color-primary-rgb), 0.3);
}

/* 背景光效 */
.glow-effect {
  position: absolute;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.3;
  z-index: 0;
  pointer-events: none;
}

.glow-1 {
  top: -20px;
  right: -20px;
  background: linear-gradient(135deg, var(--el-color-primary), #818cf8);
}

.glow-2 {
  bottom: -20px;
  left: -20px;
  background: linear-gradient(135deg, #f472b6, #a855f7);
}

/* 拖拽区域覆盖 */
.ai-upload-dragger :deep(.el-upload-dragger) {
  background: rgba(255, 255, 255, 0.4);
  border: 2px dashed rgba(0, 0, 0, 0.1);
  border-radius: 18px;
  padding: 30px 20px;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}

.dark .ai-upload-dragger :deep(.el-upload-dragger) {
  background: rgba(0, 0, 0, 0.2);
  border-color: rgba(255, 255, 255, 0.1);
}

.ai-upload-dragger :deep(.el-upload-dragger:hover),
.ai-upload-dragger :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--el-color-primary);
  background: rgba(var(--el-color-primary-rgb), 0.03);
}

/* 未上传状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
}

.icon-wrapper {
  position: relative;
  margin-bottom: 16px;
}

.upload-icon {
  font-size: 48px;
  color: var(--el-color-primary);
  z-index: 2;
  position: relative;
}

.pulse-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(var(--el-color-primary-rgb), 0.15);
  animation: pulse 2s infinite;
  z-index: 1;
}

@keyframes pulse {
  0% { transform: translate(-50%, -50%) scale(0.8); opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(1.5); opacity: 0; }
}

.empty-state .title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.empty-state .subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
}

.empty-state .subtitle span {
  color: var(--el-color-primary);
  font-weight: 600;
}

.feature-tags {
  display: flex;
  gap: 10px;
}

.feature-tags .tag {
  font-size: 11px;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--el-text-color-regular);
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.dark .feature-tags .tag {
  background: rgba(255, 255, 255, 0.05);
}

/* 已上传状态 */
.preview-state {
  position: relative;
  width: 100%;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.preview-state:hover .preview-img {
  transform: scale(1.05);
}

.preview-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.preview-state:hover .preview-overlay {
  opacity: 1;
}

.action-btn {
  margin-bottom: 8px;
  transform: scale(1.2);
}

.overlay-text {
  color: white;
  font-size: 12px;
  font-weight: 500;
}

/* 扫描线动效 */
.scan-line {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(to right, transparent, var(--el-color-primary), transparent);
  box-shadow: 0 0 15px var(--el-color-primary);
  animation: scan 2s linear infinite;
}

@keyframes scan {
  0% { top: 0%; }
  100% { top: 100%; }
}

/* 提示文字 */
.upload-tip-text {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 12px;
  text-align: center;
}

/* 进度指示 */
.processing-indicator {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.progress-bar-wrapper {
  width: 100%;
  height: 4px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 2px;
  overflow: hidden;
}

.dark .progress-bar-wrapper {
  background: rgba(255, 255, 255, 0.05);
}

.progress-bar-fill {
  height: 100%;
  width: 50%;
  background: linear-gradient(to right, var(--el-color-primary), #a855f7);
  border-radius: 2px;
  animation: progress 1.5s infinite linear;
}

@keyframes progress {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(200%); }
}

.processing-text {
  font-size: 12px;
  color: var(--el-color-primary);
  font-weight: 500;
  letter-spacing: 0.5px;
}

/* 紧凑模式 */
.compact-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  color: var(--el-text-color-regular);
}

.dark .compact-btn {
  background: rgba(30, 30, 35, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

.compact-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  transform: scale(1.05);
}

.compact-btn.loading {
  animation: spin 1s infinite linear;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

