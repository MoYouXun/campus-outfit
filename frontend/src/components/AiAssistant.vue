<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { MagicStick, Picture, Goods, Loading, Promotion, Search, Link, CircleClose } from '@element-plus/icons-vue'
import { aiAnalyze, aiChat } from '@/api/recommend'
import { getWardrobeList } from '@/api/wardrobe'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

interface Message {
  role: 'user' | 'assistant'
  content: string
  type: 'text' | 'image' | 'analysis'
  data?: any
}

const visible = ref(false)
const inputMessage = ref('')
const messages = ref<Message[]>([])
const loading = ref(false)
const sessionId = ref('')
const scrollContainer = ref<HTMLElement | null>(null)
const userStore = useUserStore()

// 引用功能增强：锁定真实 URL
const quoteContent = ref('') // 预览文字 (清洗后)
const quoteImageUrl = ref('') // 真实图片素材锁 (Base64 或 https 地址)
const closeQuote = () => { 
  quoteContent.value = '' 
  quoteImageUrl.value = ''
}

// 衣柜选择弹窗相关
const wardrobeVisible = ref(false)
const wardrobeItems = ref<any[]>([])
const wardrobeLoading = ref(false)

// 图片预览 (Lightbox 模式)
const showViewer = ref(false)
const previewUrlList = ref<string[]>([])

const openAssistant = () => {
  visible.value = true
  if (!sessionId.value) {
    sessionId.value = 'task_' + Date.now().toString()
    messages.value.push({
      role: 'assistant',
      content: '你好！我是你的 AI 校园穿搭助手。你可以点击“拍照/上传”今天的照片，或者从“衣柜选取”一件衣服，我来帮你分析并推荐搭配！',
      type: 'text'
    })
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (scrollContainer.value) {
    scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
  }
}

const fileToBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = error => reject(error)
  })
}

const onFileChange = async (e: any) => {
  const file = e.target.files[0]
  if (!file) return
  
  try {
    const base64 = await fileToBase64(file)
    handleAnalyze(base64)
  } catch (err) {
    ElMessage.error('图片读取失败')
  }
  e.target.value = ''
}

const openWardrobe = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再查看衣柜')
    return
  }
  
  wardrobeVisible.value = true
  wardrobeLoading.value = true
  try {
    const res: any = await getWardrobeList()
    wardrobeItems.value = res || []
  } catch (e: any) {
    console.error('[AiAssistant] 获取衣柜失败:', e)
    ElMessage.error(`获取衣柜失败: ${e.message || '未知错误'}`)
  } finally {
    wardrobeLoading.value = false
  }
}

const selectFromWardrobe = (item: any) => {
  wardrobeVisible.value = false
  handleAnalyze(item.originalImageUrl)
}

const handleAnalyze = async (image: string) => {
  messages.value.push({
    role: 'user',
    content: image,
    type: 'image'
  })
  
  loading.value = true
  scrollToBottom()

  try {
    const res: any = await aiAnalyze({
      base64Image: image,
      sessionId: sessionId.value
    })
    
    let resultData = res
    if (typeof res === 'string') {
      try { resultData = JSON.parse(res) } catch (e) {}
    }

    messages.value.push({
      role: 'assistant',
      content: '分析完成！这是我为你生成的专属建议：',
      type: 'analysis',
      data: resultData
    })
  } catch (e: any) {
    ElMessage.error('AI 分析失败')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return
  
  const text = inputMessage.value
  const fullText = quoteContent.value ? `${quoteContent.value}\n${text}` : text
  const currentQuoteImg = quoteImageUrl.value // 缓存当前引用的图片

  inputMessage.value = ''
  closeQuote() // 发送前重置引用状态
  
  messages.value.push({
    role: 'user',
    content: text,
    type: 'text'
  })
  
  loading.value = true
  scrollToBottom()

  try {
    // 调用 aiChat 并显式传递被引用的图片列表 (imageUrls)
    const reply: any = await aiChat({
      message: fullText,
      sessionId: sessionId.value,
      imageUrls: currentQuoteImg ? [currentQuoteImg] : []
    })
    
    try {
      const parsedData = typeof reply === 'string' ? JSON.parse(reply) : reply
      if (parsedData && (parsedData.style || parsedData.recommendations)) {
        messages.value.push({
          role: 'assistant',
          content: '已为你生成专属于你的穿搭实验室报告：',
          type: 'analysis',
          data: parsedData
        })
        return
      }
    } catch (parseError) {}

    messages.value.push({
      role: 'assistant',
      content: reply,
      type: 'text'
    })
  } catch (e) {
    ElMessage.error('发送失败')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

/**
 * 插入图片引用到输入框上方预览
 */
const insertImageReferenceToInput = (title: string, imageUrl: string) => {
  const fullStr = ` [引用图片:${title}](${imageUrl}) `
  // 正则清洗预览文字，屏蔽超长 URL 或 Base64
  quoteContent.value = fullStr.replace(/(https?:\/\/[^\s]+|data:image[^\s]+)/g, '[图片]')
  // 系统锁定该素材的原始真实数据，用于发送给 Vision 接口
  quoteImageUrl.value = imageUrl 
  
  nextTick(() => {
    const input = document.querySelector('.custom-chat-input input') as HTMLInputElement
    if (input) input.focus()
  })
}

const openCustomViewer = (url: string) => {
  previewUrlList.value = [url]
  showViewer.value = true
}

const closeViewer = () => {
  showViewer.value = false
}
</script>

<template>
  <div class="ai-assistant-wrapper">
    <!-- 悬浮入口按钮 (FAB) -->
    <div 
      class="fab-btn group animate-bounce-slow"
      @click="openAssistant"
    >
      <div class="absolute -inset-2 bg-gradient-to-r from-primary to-[#1a2a3a] rounded-full blur opacity-40 group-hover:opacity-100 transition duration-1000 group-hover:duration-200 animate-pulse"></div>
      <div class="relative w-14 h-14 bg-primary text-white rounded-full flex items-center justify-center shadow-2xl cursor-pointer hover:scale-110 transition-transform">
        <el-icon size="28"><MagicStick /></el-icon>
      </div>
    </div>

    <!-- AI 助手抽 Drawer -->
    <el-drawer
      v-model="visible"
      title="AI 校园穿搭助手"
      size="450px"
      append-to-body
      class="ai-assistant-drawer"
    >
      <div class="flex flex-col h-full overflow-hidden">
        <!-- 消息列表区 -->
        <div ref="scrollContainer" class="flex-1 overflow-y-auto p-4 space-y-6 scroll-smooth custom-scrollbar">
          <div v-for="(msg, idx) in messages" :key="idx" :class="['flex', msg.role === 'user' ? 'justify-end' : 'justify-start']">
            <div :class="['max-w-[85%] rounded-2xl p-4 shadow-sm', msg.role === 'user' ? 'bg-primary text-white rounded-tr-none' : 'bg-secondary/20 dark:bg-white/5 rounded-tl-none border border-border/50']">
              
              <!-- 文本内容 -->
              <div v-if="msg.type === 'text'" class="text-sm leading-relaxed whitespace-pre-wrap">
                {{ msg.content }}
              </div>

              <!-- 图片内容 -->
              <div v-else-if="msg.type === 'image'" class="rounded-lg overflow-hidden border-2 border-white/20 cursor-pointer" @click="openCustomViewer(msg.content)">
                <el-image :src="msg.content" class="max-w-[200px] block" />
              </div>

              <!-- 穿搭分析报告 -->
              <div v-else-if="msg.type === 'analysis' && msg.data" class="space-y-4">
                <div class="font-bold text-accent border-b border-border/30 pb-2 flex items-center gap-2">
                  <el-icon><MagicStick /></el-icon> 穿搭实验室报告
                </div>
                
                <div class="space-y-2">
                  <div class="text-xs uppercase font-black text-muted-foreground tracking-tighter opacity-60">穿搭风格 / Style</div>
                  <div class="text-lg font-black text-primary">{{ msg.data.style }}</div>
                </div>

                <div class="space-y-2">
                  <div class="text-xs uppercase font-black text-muted-foreground tracking-tighter opacity-60">核心建议 / Suggestions</div>
                  <div v-if="Array.isArray(msg.data.suggestions)" class="space-y-1">
                    <p v-for="(sug, sIdx) in msg.data.suggestions" :key="sIdx" class="text-xs leading-relaxed opacity-90">• {{ sug }}</p>
                  </div>
                  <p v-else class="text-xs leading-relaxed italic opacity-90">{{ msg.data.suggestions }}</p>
                </div>

                <!-- 推荐效果图列表 (支持 AI 引用的 ID) -->
                <div v-if="msg.data.recommendations && msg.data.recommendations.length" class="space-y-3 pt-2">
                  <div class="text-xs uppercase font-black text-muted-foreground tracking-tighter opacity-60">推荐搭配 / Recommendations</div>
                  <div class="grid grid-cols-1 gap-4">
                    <div v-for="item in msg.data.recommendations" :key="item.id || idx" class="bg-white/50 dark:bg-black/20 rounded-xl overflow-hidden border border-border/50 p-2 flex gap-3 group/item">
                      <div class="w-20 h-28 rounded-lg overflow-hidden bg-secondary/30 shrink-0 border border-white/20 relative group/img cursor-pointer" @click="openCustomViewer(item.image)">
                        <el-image 
                          :src="item.image" 
                          class="w-full h-full object-cover group-hover/item:scale-105 transition-transform duration-500" 
                          lazy
                        />
                        <!-- 悬浮操作提示 -->
                        <div class="absolute inset-0 bg-black/40 opacity-0 group-hover/img:opacity-100 flex items-center justify-center gap-4 transition-opacity duration-300">
                          <el-icon class="text-white text-xl hover:scale-125 transition-transform" title="引用此图微调" @click.stop="insertImageReferenceToInput(item.title, item.image)"><Link /></el-icon>
                        </div>
                      </div>
                      <div class="flex-1 flex flex-col justify-center gap-1">
                        <div class="text-xs font-bold text-primary">{{ item.title }}</div>
                        <p class="text-[10px] text-muted-foreground leading-tight line-clamp-3">{{ item.desc }}</p>
                        <!-- 标识当前是否引用了衣柜 ID -->
                        <div v-if="item.id" class="text-[8px] text-accent/60 italic">来源：我的衣橱 ID #{{ item.id }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 加载中动画 -->
          <div v-if="loading" class="flex justify-start">
            <div class="bg-secondary/20 rounded-2xl p-4 flex items-center gap-2">
              <el-icon class="animate-spin text-primary"><Loading /></el-icon>
              <span class="text-xs font-medium text-muted-foreground">AI 正在帮您寻找灵感...</span>
            </div>
          </div>
        </div>

        <!-- 输入操作区 -->
        <div class="p-4 border-t border-border/50 space-y-4 bg-background/50 backdrop-blur-md">
          <div class="flex gap-2">
             <label class="flex-1">
               <div class="h-10 rounded-xl flex items-center justify-center gap-2 bg-secondary/30 hover:bg-primary/10 border border-border/50 text-sm font-medium cursor-pointer transition-all text-foreground/80 active:scale-95">
                 <el-icon><Picture /></el-icon> 拍照/上传
               </div>
               <input type="file" accept="image/*" class="hidden" @change="onFileChange" />
             </label>
             <button @click="openWardrobe" class="flex-1 h-10 rounded-xl flex items-center justify-center gap-2 bg-secondary/30 hover:bg-primary/10 border border-border/50 text-sm font-medium cursor-pointer transition-all text-foreground/80 active:scale-95">
               <el-icon><Goods /></el-icon> 衣柜选取
             </button>
          </div>

          <!-- 引用显示区域 (支持自动清空素材锁) -->
          <div v-if="quoteContent" class="mb-2 px-3 py-1.5 bg-primary/5 border-l-4 border-primary rounded-r-lg flex items-center justify-between animate-in fade-in slide-in-from-left-2 transition-all">
            <div class="text-[10px] text-primary/80 truncate pr-4 italic">
              当前引用画像：{{ quoteContent }}
            </div>
            <el-icon class="cursor-pointer hover:text-red-500 transition-colors" @click="closeQuote"><CircleClose /></el-icon>
          </div>

          <!-- 聊天输入框 -->
          <div class="relative flex items-center gap-2">
            <el-input
              v-model="inputMessage"
              placeholder="发送消息咨询 AI 校园搭配..."
              @keyup.enter="sendMessage"
              :disabled="loading"
              class="custom-chat-input"
              clearable
            >
              <template #suffix>
                <el-button 
                  type="primary" 
                  circle 
                  :icon="Promotion" 
                  :loading="loading" 
                  @click="sendMessage"
                  class="send-btn"
                />
              </template>
            </el-input>
          </div>
        </div>
      </div>

      <!-- 图片预览组件 -->
      <el-image-viewer
        v-if="showViewer"
        :url-list="previewUrlList"
        :teleported="true"
        @close="closeViewer"
      />
    </el-drawer>

    <!-- 极简玻离衣柜弹窗 -->
    <el-dialog v-model="wardrobeVisible" title="选择衣橱单品" width="400px" append-to-body destroy-on-close class="glass-dialog">
       <div v-loading="wardrobeLoading" class="min-h-[300px]">
          <div v-if="wardrobeItems.length === 0" class="py-10 text-center">
             <el-empty description="衣柜还是空的呀" :image-size="80" />
          </div>
          <div v-else class="grid grid-cols-3 gap-3">
             <div 
               v-for="item in wardrobeItems" 
               :key="item.id" 
               class="aspect-[3/4] rounded-lg overflow-hidden border-2 border-transparent hover:border-primary cursor-pointer transition-all relative group"
               @click="selectFromWardrobe(item)"
             >
                <img :src="item.originalImageUrl" class="w-full h-full object-cover" />
                <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center text-[10px] text-white font-bold transition-opacity">
                  选用此单品
                </div>
             </div>
          </div>
       </div>
    </el-dialog>
  </div>
</template>

<style>
/* 继承并精简原有样式 */
.ai-assistant-drawer .el-drawer__body {
  padding: 0 !important;
}

.custom-chat-input .el-input__wrapper {
  padding-right: 4px !important;
  border-radius: 20px !important;
  box-shadow: none !important;
  background: rgba(var(--secondary-rgb), 0.3) !important;
  border: 1px solid rgba(var(--border-rgb), 0.2) !important;
}

.ai-assistant-wrapper .fab-btn {
  position: fixed;
  bottom: 80px;
  right: 24px;
  z-index: 1000;
}

.glass-dialog.el-dialog {
  border-radius: 24px !important;
  backdrop-filter: blur(20px) !important;
  background: rgba(255, 255, 255, 0.8) !important;
}
</style>
