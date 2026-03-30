<script setup lang="ts">
import { ref, onMounted } from 'vue'
import UploadImage from './UploadImage.vue'
import { ElMessage } from 'element-plus'
import { uploadAndAnalyze, publishOutfit } from '@/api/outfit'
import { useRouter } from 'vue-router'
import { getHotTopics } from '@/api/community'
import { useUserStore } from '@/stores/user'
import { MagicStick } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const topics = ref<any[]>([])
const selectedTopic = ref<number | null>(null)
const aiResult = ref<any>(null)
const uploading = ref(false)
const publishing = ref(false)

// 发布表单状态
const publishForm = ref({
  title: '',
  occasion: '日常'
})

onMounted(async () => {
  try {
    const res: any = await getHotTopics()
    topics.value = res
  } catch (e) {}
})

const handleUpload = async (data: any) => {
  uploading.value = true
  try {
    const res = await uploadAndAnalyze([data.compressedFile])
    aiResult.value = {
      ...res,
      tempImage: data.base64Data
    }
    // 初始化发布表单
    publishForm.value.title = '我的校园穿搭'
    publishForm.value.occasion = '日常'
    ElMessage.success('AI 分析完成')
  } catch (e) {
    ElMessage.error('分析失败')
  } finally {
    uploading.value = false
  }
}

const handlePublish = async (status: 'PUBLISHED' | 'PRIVATE') => {
  if (!aiResult.value) return
  
  if (!userStore.token) {
    ElMessage.warning('请先登录后再发布穿搭')
    router.push('/login')
    return
  }

  publishing.value = true
  try {
    const data = {
      title: publishForm.value.title || '我的校园穿搭',
      description: aiResult.value.proportionSuggestion,
      imageUrls: aiResult.value.imageUrls,
      styleTags: aiResult.value.styleTags,
      colorTags: aiResult.value.colorTags,
      itemKeywords: aiResult.value.itemKeywords,
      topicId: selectedTopic.value,
      occasion: publishForm.value.occasion,
      status: status
    }
    await publishOutfit(data)
    
    if (status === 'PUBLISHED') {
      ElMessage.success('已发布至社区')
      router.push('/community')
    } else {
      ElMessage.success('已保存至私人衣橱')
      aiResult.value = null
    }
  } catch (e: any) {
    console.error('发布失败错误:', e)
    ElMessage.error(e.message || '操作失败')
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="ai-analysis-card space-y-6">
    <!-- 上传区 -->
    <div class="animate-slide-up" v-loading="uploading" element-loading-text="AI 正在玩命分析中...">
      <UploadImage @upload-success="handleUpload" />
    </div>

    <!-- 结果展示区 -->
    <div class="animate-slide-up">
      <div class="glass-card p-6 min-h-[400px] flex flex-col justify-center shadow-xl">
        <div v-if="!aiResult" class="flex-center flex-col text-muted-foreground h-full opacity-60 py-10">
          <el-icon size="48" class="mb-4"><MagicStick /></el-icon>
          <p>上传图片以获取专属 AI 分析报告</p>
        </div>

        <div v-else class="animate-fade-in w-full h-full space-y-6 transform-gpu antialiased" style="backface-visibility: hidden; will-change: transform, opacity;">
          <h3 class="text-xl font-bold flex items-center gap-2">
            <el-icon class="text-primary"><MagicStick /></el-icon> 分析报告生成完毕
          </h3>
          
          <div class="flex flex-col gap-4">
            <div class="flex gap-4 items-start">
              <div class="w-24 h-32 shrink-0 rounded-lg overflow-hidden border border-border/50 shadow-sm">
                <img :src="aiResult.tempImage" alt="Uploaded Profile" class="w-full h-full object-cover" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-[10px] font-black text-primary uppercase mb-2">图片预览</div>
                <div class="grid grid-cols-4 gap-1.5">
                  <el-image v-for="url in aiResult.imageUrls" :key="url" :src="url" class="rounded-lg h-10 w-10 shadow-sm border border-white/20" fit="cover" :preview-src-list="aiResult.imageUrls" />
                </div>
              </div>
            </div>

            <div class="space-y-4 pt-2">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <div class="text-[10px] font-black text-primary uppercase mb-2">穿搭标题</div>
                  <el-input 
                    v-model="publishForm.title" 
                    placeholder="给你的穿搭起个吸引人的名字吧" 
                    maxlength="30" 
                    show-word-limit
                    size="small"
                  />
                </div>
                <div>
                  <div class="text-[10px] font-black text-primary uppercase mb-2">适合场景</div>
                  <el-select 
                    v-model="publishForm.occasion" 
                    placeholder="请选择场景" 
                    class="w-full"
                    size="small"
                  >
                    <el-option label="日常" value="日常" />
                    <el-option label="约会" value="约会" />
                    <el-option label="面试/正式" value="面试/正式" />
                    <el-option label="图书馆" value="图书馆" />
                    <el-option label="运动" value="运动" />
                  </el-select>
                </div>
              </div>

              <div>
                <div class="text-[10px] font-black text-primary uppercase mb-2">关联话题 (可选)</div>
                <el-select v-model="selectedTopic" placeholder="选择一个感兴趣的话题" class="w-full" clearable size="small">
                  <el-option v-for="t in topics" :key="t.id" :label="t.name" :value="t.id" />
                </el-select>
              </div>
              
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <div class="text-[10px] font-black text-primary uppercase mb-1">风格鉴别</div>
                  <div class="flex flex-wrap gap-1">
                    <el-tag v-for="tag in aiResult.styleTags" :key="tag" size="small" effect="plain" round>{{ tag }}</el-tag>
                  </div>
                </div>
                <div>
                  <div class="text-[10px] font-black text-primary uppercase mb-1">主要单品</div>
                  <div class="flex flex-wrap gap-1">
                    <el-tag v-for="item in aiResult.itemKeywords" :key="item" type="info" size="small" effect="plain" round>{{ item }}</el-tag>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="bg-primary/5 border border-primary/20 rounded-xl p-4 text-xs leading-relaxed">
            <span class="font-bold text-primary">AI 建议：</span>{{ aiResult.proportionSuggestion }}
          </div>
          
          <div class="flex flex-col gap-2 pt-2">
            <div class="flex gap-2">
              <el-button type="primary" :loading="publishing" @click="handlePublish('PUBLISHED')" class="flex-1" round>发布到社区</el-button>
              <el-button :loading="publishing" @click="handlePublish('PRIVATE')" class="flex-1" round>私人衣橱</el-button>
            </div>
            <el-button @click="aiResult = null" round class="w-full" plain>重新上传</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-analysis-card {
  width: 100%;
  max-width: 500px;
  margin: 0 auto;
}

.glass-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 28px;
}

.dark .glass-card {
  background: rgba(24, 24, 27, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 动画定义同步项目全局样式 */
@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fade-in 0.5s ease-out;
}

.animate-slide-up {
  animation: slide-up 0.5s ease-out;
}
</style>
