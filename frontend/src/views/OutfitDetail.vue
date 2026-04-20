<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCommunityOutfitDetail } from '@/api/community'
import { addComment, deleteComment, likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '@/api/interaction'
import { followUser, unfollowUser } from '@/api/user'
import { incrementViewCount } from '@/api/outfit'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { 
  Pointer, 
  Star, 
  View, 
  Plus, 
  Check, 
  MagicStick, 
  Close, 
  Warning, 
  Flag,
  WarnTriangleFilled,
  ChatDotRound,
  Lock,
  Monitor,
  CircleCloseFilled
} from '@element-plus/icons-vue'
import CommentItem from '../components/CommentItem.vue'

import { getImageUrl } from '@/api/image'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = route.params.id as string

const loading = ref(true)
const outfitDetail = ref<any>(null)
const nestedComments = ref<any[]>([])
const newComment = ref('')
const replyTo = ref<any>(null)

// 存储刷新后的URL
const refreshedUrls = ref(new Map<string, string>())

// 提取对象名逻辑
function extractObjectName(url: string): string | null {
  try {
    const path = url.split('?')[0]
    const segments = path.split('/')
    return segments[segments.length - 1] || null
  } catch (e) {
    return null
  }
}

// 处理图片加载失败
async function handleImageError(event: any) {
  const img = event.target as HTMLImageElement
  const currentUrl = img.src
  if (refreshedUrls.value.has(currentUrl)) return
  
  const objName = extractObjectName(currentUrl)
  if (!objName) return
  
  try {
    const res = await getImageUrl(objName)
    const newUrl = res.data
    if (newUrl) {
      refreshedUrls.value.set(currentUrl, newUrl)
      img.src = newUrl
      console.log('详情页图片URL自动修复成功:', objName)
    }
  } catch (e) {
    console.error('详情页刷新图片URL失败', e)
  }
}

const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)

const parsedAiAnalysis = computed(() => {
  const ai = outfitDetail.value?.outfit?.aiAnalysis
  if (!ai) return { suggestion: '校友暂未生成 AI 搭配灵感，快来留言分享你的看法吧~' }
  
  // 如果已经是对象（MyBatis Plus 如果配置了 TypeHandler 可能会直接返回对象）
  if (typeof ai === 'object') return ai
  
  // 如果是字符串，尝试解析 JSON
  if (typeof ai === 'string' && ai.trim().startsWith('{')) {
    try {
      return JSON.parse(ai)
    } catch (e) {
      console.warn('AI Analysis JSON Parse Error:', e)
      return { suggestion: ai }
    }
  }
  
  // 否则当作纯文本
  return { suggestion: ai }
})

const buildCommentTree = (flatComments: any[]) => {
  const map: any = {}
  const roots: any[] = []
  
  flatComments.forEach(c => {
    map[c.id] = { ...c, children: [] }
  })
  
  flatComments.forEach(c => {
    if (c.parentId && map[c.parentId]) {
      map[c.parentId].children.push(map[c.id])
    } else {
      roots.push(map[c.id])
    }
  })
  return roots
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getCommunityOutfitDetail(id, currentUserId.value)
    outfitDetail.value = res
    // 后端已经填充了用户信息，前端只需处理评论树
    nestedComments.value = buildCommentTree(res.comments || [])
    
    // 增加浏览计数（无需等待结果，不影响用户体验）
    incrementViewCount(id).catch(err => {
      console.log('增加浏览计数失败:', err)
    })
  } catch (e) {
    ElMessage.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

const handleLike = async () => {
  if (!currentUserId.value) {
    ElMessage.warning('请登录后再点赞')
    router.push('/login')
    return
  }
  try {
    if (outfitDetail.value.liked) {
      await unlikeOutfit(id, currentUserId.value)
      outfitDetail.value.outfit.likeCount--
      outfitDetail.value.liked = false
    } else {
      await likeOutfit(id, currentUserId.value)
      outfitDetail.value.outfit.likeCount++
      outfitDetail.value.liked = true
      ElMessage.success('点赞成功')
    }
  } catch (e) {}
}

const handleFavorite = async () => {
  if (!currentUserId.value) {
    ElMessage.warning('请登录后再收藏')
    router.push('/login')
    return
  }
  try {
    if (outfitDetail.value.favorited) {
      await unfavoriteOutfit(id, currentUserId.value)
      outfitDetail.value.outfit.favCount--
      outfitDetail.value.favorited = false
    } else {
      await favoriteOutfit(id, currentUserId.value)
      outfitDetail.value.outfit.favCount++
      outfitDetail.value.favorited = true
      ElMessage.success('收藏成功')
    }
  } catch (e) {}
}

const handleFollow = async () => {
  const authorId = outfitDetail.value.author.id
  try {
    if (outfitDetail.value.followingAuthor) {
      await unfollowUser(authorId)
      outfitDetail.value.followingAuthor = false
    } else {
      await followUser(authorId)
      outfitDetail.value.followingAuthor = true
      ElMessage.success('已关注作者')
    }
  } catch (e) {}
}

const handleReply = (comment: any) => {
  replyTo.value = comment
  newComment.value = ''
}

const handleDeleteComment = async (commentId: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteComment(commentId, currentUserId.value)
    ElMessage.success('评论已删除')
    loadData()
  } catch (e) {}
}

const handleComment = async () => {
  if (!currentUserId.value) {
    ElMessage.warning('请登录后再发表评论')
    router.push('/login')
    return
  }
  if (!newComment.value) return
  try {
    const data = {
      outfitId: id,
      userId: currentUserId.value,
      content: newComment.value,
      parentId: replyTo.value?.id || null,
      replyToUserId: replyTo.value?.userId || null
    }
    await addComment(data)
    newComment.value = ''
    replyTo.value = null
    ElMessage.success('评论成功')
    loadData()
  } catch (e) {}
}

const reportVisible = ref(false)
const reportLoading = ref(false)
const reportForm = ref({ reason: '色情低俗或不适宜', detail: '' })

const reportReasons = [
  { label: '色情低俗或不适宜', icon: CircleCloseFilled, desc: '包含露骨、低俗或引起不适的内容' },
  { label: '恶意引战或人身攻击', icon: WarnTriangleFilled, desc: '对他人的谩骂、歧视或挑衅行为' },
  { label: '垃圾广告或营销', icon: Monitor, desc: '频繁发布推销信息或虚假广告' },
  { label: '侵犯版权或盗图', icon: Lock, desc: '未经授权转载或盗用他人原创内容' }
]

const openReportDialog = () => {
  reportVisible.value = true
  reportForm.value.reason = '色情低俗或不适宜'
  reportForm.value.detail = ''
}

const submitReport = async () => {
  reportLoading.value = true
  try {
    await request.post('/reports', {
      targetType: 'OUTFIT',
      targetId: id,
      reason: reportForm.value.reason,
      detail: reportForm.value.detail
    })
    ElMessage.success('举报已递交风控中心审核')
    reportVisible.value = false
  } catch (e) {
    ElMessage.error('无法递交举报，请稍后重试')
  } finally {
    reportLoading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="min-h-screen bg-background/30 pt-10 pb-20">
    <div v-if="outfitDetail" class="max-w-6xl mx-auto px-4 grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
      
      <!-- Left: Image Gallery (7 cols) -->
      <div class="lg:col-span-7 space-y-6">
        <el-carousel trigger="click" height="700px" class="rounded-3xl overflow-hidden shadow-2xl group border border-border/20">
          <el-carousel-item v-for="url in (Array.isArray(outfitDetail.outfit.imageUrls) ? outfitDetail.outfit.imageUrls : JSON.parse(outfitDetail.outfit.imageUrls || '[]'))" :key="url">
            <el-image :src="url" fit="cover" class="w-full h-full hover:scale-101 transition-transform duration-700" :preview-src-list="[url]" @error="handleImageError" />
          </el-carousel-item>
        </el-carousel>
      </div>
      
      <!-- Right: Info and Comments (5 cols) -->
      <div class="lg:col-span-5 flex flex-col gap-6 sticky top-24 h-[calc(100vh-120px)]">
        <div class="glass-card p-6 flex flex-col gap-6 overflow-hidden h-full">
          <!-- Author Header Desktop -->
          <div class="flex items-center justify-between pb-4 border-b border-border/40">
            <div class="flex items-center gap-3">
              <el-avatar :size="44" :src="outfitDetail.author.avatar" class="border-2 border-primary/20" />
              <div>
                <div class="font-bold text-sm">{{ outfitDetail.author.username }}</div>
                <div class="text-[10px] text-muted-foreground">{{ outfitDetail.author.school }}</div>
              </div>
            </div>
            <el-button 
              v-if="String(outfitDetail.author.id) !== String(currentUserId)"
              :type="outfitDetail.followingAuthor ? 'info' : 'primary'" 
              round size="small" plain
              @click="handleFollow"
            >
              <el-icon class="mr-1"><component :is="outfitDetail.followingAuthor ? Check : Plus" /></el-icon>
              {{ outfitDetail.followingAuthor ? '已关注' : '关注' }}
            </el-button>
          </div>

          <!-- Content Scroll Area -->
          <div class="flex-1 overflow-y-auto pr-2 space-y-6 custom-scrollbar">
            <div>
              <h1 class="text-xl font-bold mb-3 tracking-tight">{{ outfitDetail.outfit.title || '校友的穿搭分享' }}</h1>
              <p class="text-sm text-foreground/80 leading-relaxed">{{ outfitDetail.outfit.description }}</p>
            </div>

            <div class="flex flex-wrap gap-2">
              <el-tag v-for="tag in (Array.isArray(outfitDetail.outfit.styleTags) ? outfitDetail.outfit.styleTags : JSON.parse(outfitDetail.outfit.styleTags || '[]'))" :key="tag" 
                size="small" class="rounded-lg border-primary/20 bg-primary/5 text-primary font-medium"># {{ tag }}</el-tag>
            </div>

            <div class="bg-gradient-to-br from-primary/10 to-transparent p-4 rounded-2xl border border-primary/20 relative overflow-hidden">
              <div class="text-[10px] font-black text-primary uppercase mb-2 flex items-center gap-1">
                <el-icon><MagicStick /></el-icon> AI 穿搭灵感
              </div>
              <p class="text-[13px] leading-relaxed italic text-foreground/90 leading-relaxed">
                {{ (typeof parsedAiAnalysis === 'object' && parsedAiAnalysis.suggestion) ? parsedAiAnalysis.suggestion : (typeof parsedAiAnalysis === 'string' ? parsedAiAnalysis : 'AI 正在分析这套穿搭的深度魅力...') }}
              </p>
            </div>

            <div class="flex items-center gap-6 py-2">
              <div class="flex items-center gap-1.5 cursor-pointer group" @click="handleLike">
                <div :class="['w-9 h-9 rounded-full flex-center transition-all', outfitDetail.liked ? 'bg-red-500/10 text-red-500' : 'bg-secondary text-muted-foreground group-hover:bg-primary/10 group-hover:text-primary']">
                  <el-icon size="18"><Pointer /></el-icon>
                </div>
                <span :class="['text-xs font-bold', outfitDetail.liked ? 'text-red-500' : 'text-muted-foreground']">{{ outfitDetail.outfit.likeCount }}</span>
              </div>
              <div class="flex items-center gap-1.5 cursor-pointer group" @click="handleFavorite">
                <div :class="['w-9 h-9 rounded-full flex-center transition-all', outfitDetail.favorited ? 'bg-yellow-500/10 text-yellow-500' : 'bg-secondary text-muted-foreground group-hover:bg-primary/10 group-hover:text-primary']">
                  <el-icon size="18"><Star /></el-icon>
                </div>
                <span :class="['text-xs font-bold', outfitDetail.favorited ? 'text-yellow-500' : 'text-muted-foreground']">{{ outfitDetail.outfit.favCount }}</span>
              </div>
              <el-button type="danger" link @click="openReportDialog" title="举报违规">
                <el-icon><Warning /></el-icon> 举报
              </el-button>
              <div class="flex-1"></div>
              <div class="text-[10px] text-muted-foreground flex items-center gap-1 opacity-60">
                <el-icon><View /></el-icon> {{ outfitDetail.outfit.viewCount }} 浏览
              </div>
            </div>

            <el-divider class="!my-2" />

            <div class="space-y-4">
              <div class="text-sm font-bold flex items-center gap-2">
                全部评论 <span class="text-xs font-normal text-muted-foreground opacity-70">{{ outfitDetail.outfit.commentCount }}</span>
              </div>
              <div v-if="nestedComments.length === 0" class="py-10 text-center opacity-40">
                 <p class="text-xs">还没有评论，快来抢沙发吧~</p>
              </div>
              <div v-else class="space-y-1">
                <CommentItem 
                  v-for="comment in nestedComments" 
                  :key="comment.id" 
                  :comment="comment" 
                  :currentUserId="currentUserId"
                  @reply="handleReply" 
                  @delete="handleDeleteComment"
                />
              </div>
            </div>
          </div>

          <!-- Bottom: Comment Input -->
          <div class="pt-4 border-t border-border/40">
            <div v-if="replyTo" class="mb-2 px-3 py-1 bg-secondary rounded flex justify-between items-center shadow-sm">
              <span class="text-[10px] text-muted-foreground">回复 <span class="font-bold text-foreground">@{{ replyTo.username }}</span></span>
              <el-icon class="cursor-pointer hover:text-destructive" @click="replyTo = null"><Close /></el-icon>
            </div>
            <div class="flex gap-2">
              <el-input 
                v-model="newComment" 
                :placeholder="replyTo ? `回复 @${replyTo.username}...` : '分享的想法...'" 
                class="comment-input"
                @keyup.enter="handleComment"
              />
              <el-button type="primary" :disabled="!newComment" round @click="handleComment">发布</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog 
      v-model="reportVisible" 
      title="风控中心 · 违规内容举报" 
      width="460px" 
      destroy-on-close 
      align-center
      class="report-dialog"
    >
      <div class="space-y-6">
        <div>
          <p class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-3">请选择举报原因</p>
          <div class="space-y-2">
            <div 
              v-for="item in reportReasons" 
              :key="item.label"
              class="report-reason-card"
              :class="{ 'active': reportForm.reason === item.label }"
              @click="reportForm.reason = item.label"
            >
              <div class="w-10 h-10 rounded-xl flex items-center justify-center transition-colors" :class="reportForm.reason === item.label ? 'bg-rose-500 text-white' : 'bg-slate-100 text-slate-400'">
                <el-icon :size="20"><component :is="item.icon" /></el-icon>
              </div>
              <div class="flex-1">
                <p class="text-sm font-bold leading-tight" :class="reportForm.reason === item.label ? 'text-slate-800' : 'text-slate-600'">{{ item.label }}</p>
                <p class="text-[10px] opacity-60 mt-0.5">{{ item.desc }}</p>
              </div>
              <div v-if="reportForm.reason === item.label" class="animate-in fade-in zoom-in duration-300">
                <el-icon class="text-rose-500" size="18"><Check /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <div>
          <p class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-3">详细描述 (可选)</p>
          <el-input
            v-model="reportForm.detail"
            type="textarea"
            :rows="3"
            placeholder="请提供更多细节，帮助审核员快速判断..."
            maxlength="200"
            show-word-limit
            class="report-textarea"
          />
        </div>
      </div>

      <template #footer>
        <div class="flex gap-3 pt-2">
          <el-button class="flex-1 !h-12 !rounded-xl font-bold" @click="reportVisible = false">取消</el-button>
          <el-button 
            type="danger" 
            class="flex-1 !h-12 !rounded-xl font-bold shadow-lg shadow-rose-500/20" 
            :loading="reportLoading" 
            @click="submitReport"
          >
            提交风控审核
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.glass-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 24px;
}
.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 10px; }

.report-reason-card {
  @apply flex items-center gap-4 p-4 rounded-2xl border border-slate-100 cursor-pointer transition-all duration-300;
}
.report-reason-card:hover {
  @apply bg-slate-50 scale-[1.01] border-slate-200;
}
.report-reason-card.active {
  @apply bg-rose-50/50 border-rose-200 shadow-sm shadow-rose-200/20 translate-x-1;
}

:deep(.report-textarea .el-textarea__inner) {
  @apply !rounded-2xl !bg-slate-50 !border-slate-100 focus:!border-rose-200 focus:!bg-white transition-all p-4 text-sm;
}

:deep(.report-dialog) {
  @apply !rounded-[32px] overflow-hidden;
}
:deep(.report-dialog .el-dialog__header) {
  @apply !pt-8 !px-8 !pb-0;
}
:deep(.report-dialog .el-dialog__title) {
  @apply text-lg font-black text-slate-800;
}
:deep(.report-dialog .el-dialog__body) {
  @apply !p-8;
}
:deep(.report-dialog .el-dialog__footer) {
  @apply !px-8 !pb-8 !pt-0;
}
</style>
