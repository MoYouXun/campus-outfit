<script setup lang="ts">
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import Compressor from 'compressorjs'
import type { UploadRequestOptions, UploadFile } from 'element-plus'

const emit = defineEmits(['upload-success'])
const isUploading = ref(false)
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

// 采用覆盖 Element Plus 默认上传逻辑的手动上传模式
const customUpload = (options: UploadRequestOptions) => {
  const file = options.file
  isUploading.value = true

  // 使用 Compressor.js 进行客户端拦截与压缩 (目标 < 500KB)
  new Compressor(file, {
    quality: 0.6, // 降低质量
    maxWidth: 1200, // 限制长边不超于 1200
    success: async (compressedResult: File | Blob) => {
      try {
        const compressedSize = (compressedResult.size / 1024 / 1024).toFixed(2)
        ElMessage.success(`客户端图片压缩完成，大小减至 ${compressedSize}MB.`)
        
        console.log('压缩结果类型:', typeof compressedResult)
        console.log('压缩结果是否为Blob:', compressedResult instanceof Blob)
        console.log('压缩结果是否为File:', compressedResult instanceof File)
        
        // 生成预览图
        previewImage.value = await fileToBase64(compressedResult)
        
        // 确保压缩结果是File类型
        let finalFile: File
        if (compressedResult instanceof File) {
          finalFile = compressedResult
        } else {
          // 将Blob转换为File
          finalFile = new File([compressedResult], options.file.name, {
            type: compressedResult.type || 'image/jpeg'
          })
          console.log('已将Blob转换为File:', finalFile)
        }
        
        // 模拟向后端传输或直接将压缩后的 Base64 抛出给上层组件
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
</script>

<template>
  <div class="glass-card p-6 w-full max-w-lg mx-auto relative overflow-hidden transition-all duration-300 hover:shadow-xl">
    <!-- 背景光效点缀 -->
    <div class="absolute -top-10 -right-10 w-32 h-32 bg-primary/20 rounded-full blur-3xl"></div>
    <div class="absolute -bottom-10 -left-10 w-32 h-32 bg-purple-500/20 rounded-full blur-3xl"></div>
    
    <div class="relative z-10">
      <h2 class="text-2xl font-bold mb-4 text-gradient">获取穿搭建议</h2>
      <p class="text-sm text-muted-foreground mb-6">上传您的穿搭照片，AI 将为您智能提取风格并给出搭配建议。系统会在浏览器端自动压缩图片以节省流量。</p>
      
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :http-request="customUpload"
        :show-file-list="false"
        accept="image/*"
        :disabled="isUploading"
      >
        <template v-if="!previewImage">
          <el-icon class="el-icon--upload text-primary"><upload-filled /></el-icon>
          <div class="el-upload__text mt-2">
            拖拽图片到此处，或<em class="text-primary font-semibold">点击上传</em>
          </div>
        </template>
        
        <template v-else>
          <div class="relative w-full h-full flex items-center justify-center p-2">
            <img :src="previewImage" alt="Preview" class="max-h-56 object-contain rounded-lg shadow-sm" />
            <div class="absolute inset-0 bg-black/40 opacity-0 hover:opacity-100 flex-center transition-opacity duration-300 rounded-lg">
              <span class="text-white font-medium flex items-center gap-2">
                <el-icon><Refresh /></el-icon> 重新上传
              </span>
            </div>
          </div>
        </template>
        
        <template #tip>
          <div class="el-upload__tip text-xs mt-2 text-center">
            支持 JPG/PNG 等格式，客户端自动优化体积
          </div>
        </template>
      </el-upload>
      
      <!-- 压缩/上传过程指示器 -->
      <div v-if="isUploading" class="mt-4 animate-fade-in flex-center flex-col gap-2">
        <el-progress :percentage="80" :show-text="false" status="warning" class="w-full" :indeterminate="true" />
        <span class="text-xs text-muted-foreground animate-pulse">正在压缩与提取 AI 标签...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.upload-demo :deep(.el-upload-dragger) {
  @apply bg-transparent border-dashed border-2 border-border rounded-xl transition-all duration-300 hover:border-primary hover:bg-primary/5 dark:hover:bg-primary/10 relative overflow-hidden;
}

.upload-demo :deep(.el-upload-dragger.is-dragover) {
  @apply border-primary bg-primary/10;
}
</style>
