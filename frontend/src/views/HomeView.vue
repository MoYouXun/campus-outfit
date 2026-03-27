<script setup lang="ts">
import { ref, onMounted } from 'vue'
import UploadImage from '../components/UploadImage.vue'
import { ElMessage } from 'element-plus'
import { uploadAndAnalyze, publishOutfit } from '@/api/outfit'
import { useRouter } from 'vue-router'
import { getHotTopics } from '@/api/community'

import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const topics = ref<any[]>([])
const selectedTopic = ref<number | null>(null)



onMounted(async () => {
  try {
    const res: any = await getHotTopics()
    topics.value = res
  } catch (e) {}
})

const aiResult = ref<any>(null)
const uploading = ref(false)
const publishing = ref(false)

const handleUpload = async (data: any) => {
  uploading.value = true
  try {
    const res = await uploadAndAnalyze([data.compressedFile])
    aiResult.value = {
      ...res,
      tempImage: data.base64Data
    }
    ElMessage.success('AI 分析完成')
  } catch (e) {
    ElMessage.error('分析失败')
  } finally {
    uploading.value = false
  }
}

const handlePublish = async () => {
  if (!aiResult.value) return
  
  // 增加登录校验
  if (!userStore.token) {
    ElMessage.warning('请先登录后再发布穿搭')
    router.push('/login')
    return
  }

  publishing.value = true
  try {
    const data = {
      title: '我的校园穿搭',
      description: aiResult.value.proportionSuggestion,
      imageUrls: aiResult.value.imageUrls,
      styleTags: aiResult.value.styleTags,
      colorTags: aiResult.value.colorTags,
      itemKeywords: aiResult.value.itemKeywords,
      topicId: selectedTopic.value,
      status: 'PUBLISHED',
      isPublic: true
    }
    await publishOutfit(data)
    ElMessage.success('发布成功')
    router.push('/community')
  } catch (e: any) {
    console.error('发布失败错误:', e)
    ElMessage.error(e.message || '发布失败')
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-background/50 flex flex-col items-center pt-8 pb-20 px-4 sm:px-6 lg:px-8">
    
    <!-- Hero Section -->
    <div class="text-center max-w-3xl mx-auto mb-12 animate-fade-in relative">
      <div class="absolute -top-6 -left-10 w-20 h-20 bg-primary/20 rounded-full blur-2xl"></div>
      <h1 class="text-4xl sm:text-5xl font-extrabold tracking-tight text-foreground mb-4">
        AI <span class="text-gradient">校园穿搭</span> 智能助手
      </h1>
      <p class="text-lg text-muted-foreground">
        不再为每天“穿什么”而烦恼。利用领先的 AI 多模态大模型结合实时天气，智能解析衣橱并为您推荐最匹配的个性穿搭策略。
      </p>
    </div>



    <!-- Main Content Grid -->
    <div class="w-full max-w-5xl grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
      <div class="animate-slide-up" v-loading="uploading" element-loading-text="AI 正在玩命分析中...">
        <UploadImage @upload-success="handleUpload" />
      </div>

      <div class="animate-slide-up">
        <div class="glass-card p-6 h-full flex flex-col justify-center min-h-[400px]">
          <div v-if="!aiResult" class="flex-center flex-col text-muted-foreground h-full opacity-60">
            <el-icon size="48" class="mb-4"><MagicStick /></el-icon>
            <p>上传图片以获取专属 AI 分析报告</p>
          </div>

          <div v-else class="animate-fade-in w-full h-full space-y-6">
            <h3 class="text-xl font-bold flex items-center gap-2">
              <el-icon class="text-primary"><MagicStick /></el-icon> 分析报告生成完毕
            </h3>
            
            <div class="flex gap-4 items-start">
              <div class="w-1/3 shrink-0 rounded-lg overflow-hidden border border-border/50">
                <img :src="aiResult.tempImage" alt="Uploaded Profile" class="w-full h-auto object-cover" />
              </div>
              <div class="space-y-4">
                <div>
                  <div class="text-[10px] font-black text-primary uppercase mb-2">图片预览</div>
                  <div class="grid grid-cols-4 gap-2">
                    <el-image v-for="url in aiResult.imageUrls" :key="url" :src="url" class="rounded-lg h-20 w-20" fit="cover" />
                  </div>
                </div>

                <div>
                  <div class="text-[10px] font-black text-primary uppercase mb-2">关联话题 (可选)</div>
                  <el-select v-model="selectedTopic" placeholder="选择一个感兴趣的话题" class="w-full" clearable>
                    <el-option v-for="t in topics" :key="t.id" :label="t.name" :value="t.id" />
                  </el-select>
                </div>
                <div>
                  <div class="text-xs font-semibold text-muted-foreground uppercase mb-1">风格鉴别</div>
                  <div class="flex flex-wrap gap-2">
                    <el-tag v-for="tag in aiResult.styleTags" :key="tag" size="small">{{ tag }}</el-tag>
                  </div>
                </div>
                <div>
                  <div class="text-xs font-semibold text-muted-foreground uppercase mb-1">主要单品</div>
                  <div class="flex flex-wrap gap-2">
                    <el-tag v-for="item in aiResult.itemKeywords" :key="item" type="info" size="small">{{ item }}</el-tag>
                  </div>
                </div>
              </div>
            </div>

            <div class="bg-primary/5 border border-primary/20 rounded-xl p-4 text-sm leading-relaxed">
              <span class="font-bold text-primary">AI 建议：</span>{{ aiResult.proportionSuggestion }}
            </div>
            
            <div class="flex gap-3 pt-2">
              <el-button @click="aiResult = null" round class="w-full">重新上传</el-button>
              <el-button type="primary" :loading="publishing" @click="handlePublish" class="w-full" round>发布至社区</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>