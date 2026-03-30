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
const quoteContent = ref('') // 引用内容预览
const closeQuote = () => { quoteContent.value = '' }

// 衣柜选择弹窗相关
const wardrobeVisible = ref(false)
const wardrobeItems = ref<any[]>([])
const wardrobeLoading = ref(false)

// 自定义图片预览 (解耦方案)
const showViewer = ref(false)
const previewUrlList = ref<string[]>([])

const openAssistant = () => {
  visible.value = true
  if (!sessionId.value) {
    sessionId.value = 'task_' + Date.now().toString()
    // 初始欢迎语
    messages.value.push({
      role: 'assistant',
      content: '你好！我是你的 AI 校园穿搭助手。你可以上传一张今天的照片，或者从衣柜里选一件衣服，我来帮你分析并推荐搭配！',
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

// 图片转 Base64 辅助函数
const fileToBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = error => reject(error)
  })
}

// 修改图片上传并分析
const onFileChange = async (e: any) => {
  const file = e.target.files[0]
  if (!file) return
  
  try {
    const base64 = await fileToBase64(file)
    handleAnalyze(base64)
  } catch (err) {
    ElMessage.error('图片读取失败')
  }
  e.target.value = '' // 清空 input
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
  // 添加用户图片消息
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
    
    // 解析返回的 JSON 字符串
    let resultData = res
    if (typeof res === 'string') {
      try {
        resultData = JSON.parse(res)
      } catch (e) {
        // 如果解析失败，可能是普通文本回复
      }
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

  inputMessage.value = ''
  closeQuote() // 发送后重置引用
  
  messages.value.push({
    role: 'user',
    content: text,
    type: 'text'
  })
  
  loading.value = true
  scrollToBottom()

  try {
    const reply: any = await aiChat({
      message: fullText,
      sessionId: sessionId.value
    })
    
    // 智能解析逻辑：尝试将响应解析为结构化搭配方案
    try {
      const parsedData = typeof reply === 'string' ? JSON.parse(reply) : reply
      // 校验穿搭方案架构：包含风格且推荐列表为数组
      if (parsedData && parsedData.style && Array.isArray(parsedData.recommendations)) {
        messages.value.push({
          role: 'assistant',
          content: '已为你生成专属于你的穿搭实验室报告：',
          type: 'analysis',
          data: parsedData
        })
        return // 解析成功并推送后退出逻辑
      }
    } catch (parseError) {
      // 解析失败说明是普通对话文本，进入下方的默认逻辑
    }

    // 默认回退：作为普通文本消息展示
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

const insertImageReferenceToInput = (title: string, imageUrl: string) => {
  const fullStr = ` [引用图片:${title}](${imageUrl}) `
  // 【正则清洗】屏蔽所有的 http(s) 链接以及 data:image 的 Base64 长串，替换为 [图片]
  quoteContent.value = fullStr.replace(/(https?:\/\/[^\s]+|data:image[^\s]+)/g, '[图片]')
  
  // 自动聚焦输入框
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
      <div class="absolute -inset-2 bg-gradient-to-r from-primary to-indigo-500 rounded-full blur opacity-40 group-hover:opacity-100 transition duration-1000 group-hover:duration-200 animate-pulse"></div>
      <div class="relative w-14 h-14 bg-primary text-white rounded-full flex items-center justify-center shadow-2xl cursor-pointer hover:scale-110 transition-transform">
        <el-icon size="28"><MagicStick /></el-icon>
      </div>
    </div>

    <!-- AI 助手抽屉 -->
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

              <!-- 复杂的分析报告 -->
              <div v-else-if="msg.type === 'analysis' && msg.data" class="space-y-4">
                <div class="font-bold text-accent border-b border-border/30 pb-2 flex items-center gap-2">
                  <el-icon><MagicStick /></el-icon> 穿搭实验室建议
                </div>
                
                <div class="space-y-2">
                  <div class="text-xs uppercase font-black text-muted-foreground tracking-tighter">穿搭风格 / Style</div>
                  <div class="text-lg font-black text-primary">{{ msg.data.style }}</div>
                </div>

                <div class="space-y-2">
                  <div class="text-xs uppercase font-black text-muted-foreground tracking-tighter">分析建议 / Suggestions</div>
                  <div v-if="Array.isArray(msg.data.suggestions)" class="space-y-1">
                    <p v-for="(sug, sIdx) in msg.data.suggestions" :key="sIdx" class="text-xs leading-relaxed opacity-80">• {{ sug }}</p>
                  </div>
                  <p v-else class="text-xs leading-relaxed italic opacity-80">{{ msg.data.suggestions }}</p>
                </div>

                <!-- 推荐效果图列表 -->
                <div v-if="msg.data.recommendations && msg.data.recommendations.length" class="space-y-3 pt-2">
                  <div class="text-xs uppercase font-black text-muted-foreground tracking-tighter">推荐搭配 / Recommendations</div>
                  <div class="grid grid-cols-1 gap-4">
                    <div v-for="item in msg.data.recommendations" :key="item.id" class="bg-white/50 dark:bg-black/20 rounded-xl overflow-hidden border border-border/50 p-2 flex gap-3 group/item">
                      <div class="w-20 h-28 rounded-lg overflow-hidden bg-secondary/30 shrink-0 border border-white/20 relative group/img cursor-pointer" @click="openCustomViewer(item.image)">
                        <el-image 
                          :src="item.image" 
                          class="w-full h-full object-cover group-hover/item:scale-105 transition-transform duration-500" 
                          hide-on-click-modal 
                          style="transform: translateZ(0); will-change: transform; backface-visibility: hidden;" 
                        />
                        <!-- 显式放大与引用按钮提示 -->
                        <div class="absolute inset-0 bg-black/40 opacity-0 group-hover/img:opacity-100 flex items-center justify-center gap-4 transition-opacity">
                          <el-icon class="text-white text-xl hover:scale-125 transition-transform" title="查看大图"><Search /></el-icon>
                          <el-icon class="text-white text-xl hover:scale-125 transition-transform" title="引用此单品" @click.stop="insertImageReferenceToInput(item.title, item.image)"><Link /></el-icon>
                        </div>
                      </div>
                      <div class="flex-1 flex flex-col justify-center gap-1">
                        <div class="text-xs font-bold text-primary">{{ item.title }}</div>
                        <p class="text-[10px] text-muted-foreground leading-tight">{{ item.desc }}</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- AI 思考中加载态 -->
          <div v-if="loading" class="flex justify-start">
            <div class="bg-secondary/20 rounded-2xl p-4 flex items-center gap-2">
              <el-icon class="animate-spin text-primary"><Loading /></el-icon>
              <span class="text-xs font-medium text-muted-foreground">AI 正在为你施展魔法...</span>
            </div>
          </div>
        </div>

        <!-- 输入操作区 -->
        <div class="p-4 border-t border-border/50 space-y-4 bg-background/50 backdrop-blur-md">
          <!-- 快捷工具栏 -->
          <div class="flex gap-2">
             <label class="flex-1">
               <div class="h-10 rounded-xl flex items-center justify-center gap-2 bg-secondary/30 hover:bg-primary/10 border border-border/50 text-sm font-medium cursor-pointer transition-colors text-foreground/80">
                 <el-icon><Picture /></el-icon> 拍照/上传
               </div>
               <input type="file" accept="image/*" class="hidden" @change="onFileChange" />
             </label>
             <button @click="openWardrobe" class="flex-1 h-10 rounded-xl flex items-center justify-center gap-2 bg-secondary/30 hover:bg-primary/10 border border-border/50 text-sm font-medium cursor-pointer transition-colors text-foreground/80">
               <el-icon><Goods /></el-icon> 衣柜选取
             </button>
          </div>

          <!-- 引用显示区域 -->
          <div v-if="quoteContent" class="mb-2 px-3 py-1.5 bg-primary/5 border-l-4 border-primary rounded-r-lg flex items-center justify-between">
            <div class="text-[10px] text-primary/80 truncate pr-4 italic">
              当前引用：{{ quoteContent }}
            </div>
            <el-icon class="cursor-pointer hover:text-red-500 transition-colors" @click="closeQuote"><CircleClose /></el-icon>
          </div>

          <!-- 文本输入框 -->
          <div class="relative flex items-center gap-2">
            <el-input
              v-model="inputMessage"
              placeholder="问问 AI 搭配建议..."
              @keyup.enter="sendMessage"
              :disabled="loading"
              class="custom-chat-input"
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
      <!-- 自定义图片预览器 (受控渲染，防止重排闪烁) -->
      <el-image-viewer
        v-if="showViewer"
        :url-list="previewUrlList"
        :teleported="true"
        @close="closeViewer"
      />
    </el-drawer>

    <!-- 衣柜选取单品弹窗 -->
    <el-dialog v-model="wardrobeVisible" title="从电子衣橱挑选单品" width="400px" append-to-body destroy-on-close class="glass-dialog shadow-huge">
       <div v-loading="wardrobeLoading" class="min-h-[300px]">
          <div v-if="wardrobeItems.length === 0" class="py-10">
             <el-empty description="衣柜还是空的喔" :image-size="80" />
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
                  选择此件
                </div>
             </div>
          </div>
       </div>
    </el-dialog>
  </div>
</template>

<style>
.ai-assistant-drawer .el-drawer__body {
  padding: 0 !important;
  background: radial-gradient(circle at 100% 100%, rgba(var(--primary-rgb), 0.05) 0%, transparent 40%),
              rgba(var(--background-rgb), 1);
}

.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(var(--primary-rgb), 0.2);
  border-radius: 4px;
}

.custom-chat-input .el-input__wrapper {
  padding-right: 4px !important;
  border-radius: 20px !important;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05) !important;
  background: rgba(var(--secondary-rgb), 0.3) !important;
}

.ai-assistant-wrapper .fab-btn {
  position: fixed;
  bottom: 80px;
  right: 24px;
  z-index: 1000;
}

@keyframes bounce-slow {
  0%, 100% { transform: translateY(-5%); animation-timing-function: cubic-bezier(0.8, 0, 1, 1); }
  50% { transform: translateY(0); animation-timing-function: cubic-bezier(0, 0, 0.2, 1); }
}

.animate-bounce-slow {
  animation: bounce-slow 3s infinite;
}

.glass-dialog.el-dialog {
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.8) !important;
  backdrop-filter: blur(20px);
}
</style>
