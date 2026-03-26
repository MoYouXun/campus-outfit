<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Upload, UploadProps, ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import Compressor from 'compressorjs'
import request from '@/utils/request'

// 定义组件的 Props
interface ImageUploadProps {
  limit?: number
  multiple?: boolean
  accept?: string
  maxSize?: number // MB
}

const props = withDefaults(defineProps<ImageUploadProps>(), {
  limit: 1,
  multiple: false,
  accept: 'image/*',
  maxSize: 5 // 默认最大 5MB
})

// 定义组件的 Emits
interface ImageUploadEmits {
  (e: 'success', files: any[]): void
  (e: 'error', error: any): void
}

const emit = defineEmits<ImageUploadEmits>()

// 上传列表
const fileList = ref<UploadProps['fileList']>([])

// 预览对话框显示状态
const previewVisible = ref(false)

// 当前预览的图片 URL
const previewImage = ref('')

// 上传进度
const uploadProgress = reactive<{ [key: string]: number }>({})

// 压缩图片
const compressImage = (file: File): Promise<File> => {
  return new Promise((resolve, reject) => {
    new Compressor(file, {
      quality: 0.8, // 初始质量
      maxWidth: 1920,
      maxHeight: 1920,
      convertSize: 500 * 1024, // 500KB 以内不压缩
      success: (compressedFile) => {
        // 将压缩后的 Blob 转换为 File
        const file = new File([compressedFile as Blob], compressedFile.name || 'compressed.jpg', {
          type: compressedFile.type || 'image/jpeg',
          lastModified: Date.now()
        })
        resolve(file)
      },
      error: (error) => {
        console.error('Compression Error:', error)
        reject(error)
      }
    })
  })
}

// 自定义上传函数
const handleUpload = async (rawFile: File) => {
  try {
    // 压缩图片
    const compressedFile = await compressImage(rawFile)
    
    // 创建 FormData
    const formData = new FormData()
    formData.append('file', compressedFile)
    
    // 上传图片到服务器
    const response = await request({
      url: '/outfit/upload',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100)
          uploadProgress[rawFile.uid] = percent
        }
      }
    })
    
    // 处理上传成功
    const file = {
      uid: rawFile.uid,
      name: rawFile.name,
      status: 'success',
      url: response.url || '',
      response
    }
    
    fileList.value.push(file)
    emit('success', [file])
    ElMessage.success('图片上传成功')
    
  } catch (error: any) {
    console.error('Upload Error:', error)
    const file = {
      uid: rawFile.uid,
      name: rawFile.name,
      status: 'fail',
      response: error
    }
    
    fileList.value.push(file)
    emit('error', error)
    ElMessage.error(error.message || '图片上传失败')
  }
}

// 图片上传前的处理
const beforeUpload = async (file: File) => {
  // 检查文件类型
  if (props.accept && !file.type.match(props.accept)) {
    ElMessage.error('请选择正确的图片格式')
    return false
  }
  
  // 检查文件大小
  if (file.size / 1024 / 1024 > props.maxSize) {
    ElMessage.error(`图片大小不能超过 ${props.maxSize}MB`)
    return false
  }
  
  // 拦截默认上传行为，使用自定义上传
  await handleUpload(file)
  return false
}

// 移除上传文件
const handleRemove = (file: any) => {
  const index = fileList.value.findIndex(item => item.uid === file.uid)
  if (index !== -1) {
    fileList.value.splice(index, 1)
  }
}

// 处理图片预览
const handlePreview = (file: any) => {
  previewImage.value = file.url || file.response.url
  previewVisible.value = true
}
</script>

<template>
  <div class="image-upload-container">
    <el-upload
      v-model:file-list="fileList"
      :accept="accept"
      :multiple="multiple"
      :limit="limit"
      :auto-upload="false"
      :on-remove="handleRemove"
      :on-preview="handlePreview"
      :before-upload="beforeUpload"
      list-type="picture-card"
    >
      <el-icon><Plus /></el-icon>
      <template #tip>
        <div class="el-upload__tip">
          请上传图片文件，单张图片大小不超过 {{ maxSize }}MB
        </div>
      </template>
    </el-upload>
    
    <!-- 图片预览 -->
    <el-dialog
      v-model="previewVisible"
      title="图片预览"
      width="800px"
    >
      <img width="100%" :src="previewImage" alt="Preview">
    </el-dialog>
  </div>
</template>

<style scoped>
.image-upload-container {
  width: 100%;
}
</style>