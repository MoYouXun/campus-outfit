<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import MasonryGallery from '../components/MasonryGallery.vue'
import { ElMessage, ElScrollbar, ElMessageBox } from 'element-plus'
import { Loading, Plus } from '@element-plus/icons-vue'
import AiAnalysisCard from '@/components/AiAnalysisCard.vue'
import { getCommunityFeed, getHotTopics, getFollowingFeed } from '../api/community'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '../api/interaction'
import { deleteOutfit, updateOutfitStatus } from '../api/outfit'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const activeTab = ref('recommend')
const topics = ref<any[]>([])
const selectedTopicId = ref<number | null>(null)
const displayList = ref<any[]>([])
const loading = ref(false)
const noMore = ref(false)
const page = ref(1)

const userStore = useUserStore()
const router = useRouter()
const isAnalysisVisible = ref(false)
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)

const loadTopics = async () => {
  try {
    const res: any = await getHotTopics()
    topics.value = res
  } catch (e) {}
}

const loadData = async (reset = true) => {
  if (reset) {
    page.value = 1
    displayList.value = []
  }
  loading.value = true
  try {
    let res: any
    if (activeTab.value === 'following') {
      if (!userStore.token) {
        displayList.value = []
        noMore.value = true
        loading.value = false
        return
      }
      res = await getFollowingFeed({ 
        currentUserId: currentUserId.value, 
        page: page.value, 
        size: 10 
      })
    } else {
      const sortBy = activeTab.value === 'recommend' ? 'hot' : 'new'
      res = await getCommunityFeed({ 
        page: page.value, 
        size: 10, 
        sortBy,
        topicId: selectedTopicId.value,
        currentUserId: currentUserId.value
      })
    }
    if (res && res.records) {
      const records = res.records.map((item: any) => ({
        ...item,
        imageUrls: Array.isArray(item.imageUrls) ? item.imageUrls : (item.imageUrls ? JSON.parse(item.imageUrls) : []),
        styleTags: Array.isArray(item.styleTags) ? item.styleTags : (item.styleTags ? JSON.parse(item.styleTags) : [])
      }))
      displayList.value = reset ? records : [...displayList.value, ...records]
      noMore.value = records.length < 10
    }
  } catch (error) {
    ElMessage.error('加载社区内容失败')
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  if (loading.value || noMore.value) return
  page.value++
  loadData(false)
}

onMounted(() => {
  loadData()
  loadTopics()
})

const handleTabChange = (tab: string) => {
  activeTab.value = tab
  selectedTopicId.value = null
  loadData()
}

const handleTopicSelect = (id: number | null) => {
  selectedTopicId.value = id
  loadData()
}

const handleLike = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
    router.push('/login')
    return
  }
  try {
    if (item.liked) {
      await unlikeOutfit(item.id, currentUserId.value!)
      item.likeCount = Math.max(0, (item.likeCount || 0) - 1)
      item.liked = false
    } else {
      await likeOutfit(item.id, currentUserId.value!)
      item.likeCount = (item.likeCount || 0) + 1
      item.liked = true
      ElMessage({ message: '点赞成功！', type: 'success', grouping: true })
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleFavorite = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
    router.push('/login')
    return
  }
  try {
    if (item.favorited) {
      await unfavoriteOutfit(item.id, currentUserId.value!)
      item.favCount = Math.max(0, (item.favCount || 0) - 1)
      item.favorited = false
    } else {
      await favoriteOutfit(item.id, currentUserId.value!)
      item.favCount = (item.favCount || 0) + 1
      item.favorited = true
      ElMessage({ message: '收藏成功！', type: 'success', grouping: true })
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条穿搭吗？删除后不可恢复', '删除提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'glass-message-box'
    })
    
    await deleteOutfit(id)
    ElMessage.success('删除成功')
    
    // 从本地列表中移除
    displayList.value = displayList.value.filter(o => o.id !== id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败，请稍后重试')
    }
  }
}

const handleToggleStatus = async (outfit: any, newStatus: string) => {
  try {
    await updateOutfitStatus(outfit.id, newStatus as any)
    ElMessage.success('已移入私人衣橱')
    const index = displayList.value.findIndex(item => item.id === outfit.id)
    if (index !== -1) {
      displayList.value.splice(index, 1)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="min-h-screen bg-background pt-10 pb-20 px-4 sm:px-6 lg:px-8">
    <div class="max-w-7xl mx-auto">
      
      <!-- Header Section -->
      <div class="flex flex-col md:flex-row justify-between items-center mb-10 gap-4 animate-fade-in">
        <div>
          <h2 class="text-3xl font-bold tracking-tight mb-2">灵感社区</h2>
          <p class="text-muted-foreground">发现校园最 In 的穿搭趋势与灵感</p>
        </div>
        
        <div class="flex p-1 bg-secondary/50 backdrop-blur-sm rounded-xl border border-white/20">
          <button 
            @click="handleTabChange('recommend')"
            :class="[
              'px-6 py-2 rounded-lg text-sm font-medium transition-all duration-300',
              activeTab === 'recommend' && !selectedTopicId ? 'bg-white dark:bg-gray-800 text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
            ]"
          >
            本周热榜
          </button>
          <button 
            @click="handleTabChange('latest')"
            :class="[
              'px-6 py-2 rounded-lg text-sm font-medium transition-all duration-300',
              activeTab === 'latest' && !selectedTopicId ? 'bg-white dark:bg-gray-800 text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
            ]"
          >
            最新动态
          </button>
          <button 
            v-if="userStore.token"
            @click="handleTabChange('following')"
            :class="[
              'px-6 py-2 rounded-lg text-sm font-medium transition-all duration-300',
              activeTab === 'following' && !selectedTopicId ? 'bg-white dark:bg-gray-800 text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
            ]"
          >
            关注
          </button>
        </div>
      </div>

      <!-- Topic Bar -->
      <div class="flex items-center gap-2 mb-8 overflow-x-auto pb-2 custom-scrollbar animate-fade-in-up">
        <button 
          @click="handleTopicSelect(null)"
          :class="['px-4 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all border', 
            !selectedTopicId ? 'bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/20' : 'bg-secondary text-muted-foreground border-transparent hover:border-border']"
        >
          全部
        </button>
        <button 
          v-for="topic in topics" 
          :key="topic.id"
          @click="handleTopicSelect(topic.id)"
          :class="['px-4 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all border', 
            selectedTopicId === topic.id ? 'bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/20' : 'bg-secondary text-muted-foreground border-transparent hover:border-border']"
        >
          # {{ topic.name }}
        </button>
      </div>

      <!-- Gallery -->
      <el-scrollbar 
        class="min-h-[400px]"
        :infinite-scroll="loadMore"
        :infinite-scroll-disabled="loading || noMore"
        :infinite-scroll-distance="200"
      >
        <div v-if="loading && displayList.length === 0" class="flex-center py-20">
          <el-icon class="is-loading text-4xl text-primary"><Loading /></el-icon>
        </div>
        <MasonryGallery 
          v-else 
          :outfits="displayList" 
          @like="handleLike" 
          @favorite="handleFavorite"
          @delete="handleDelete"
          @toggle-status="(item) => handleToggleStatus(item, 'PRIVATE')"
        />
      </el-scrollbar>

    </div>

    <!-- AI 穿搭助手悬浮按钮 -->
    <el-button 
      type="primary" 
      circle 
      :icon="Plus" 
      class="floating-action-btn shadow-2xl"
      @click="isAnalysisVisible = true"
    />

    <!-- AI 分析对话框 -->
    <el-dialog 
      v-model="isAnalysisVisible" 
      title="AI 校园穿搭助手" 
      width="550px" 
      append-to-body
      destroy-on-close
      class="glass-dialog"
    >
      <div class="px-2 pb-6">
        <AiAnalysisCard />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.floating-action-btn {
  position: fixed;
  bottom: 40px;
  right: 40px;
  z-index: 999;
  width: 64px;
  height: 64px;
  font-size: 28px;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  box-shadow: 0 10px 25px -5px rgba(var(--el-color-primary-rgb), 0.5);
  background: linear-gradient(135deg, var(--el-color-primary) 0%, #a855f7 100%);
  border: none;
}

.floating-action-btn:hover {
  transform: scale(1.1) rotate(90deg);
  box-shadow: 0 15px 30px -5px rgba(var(--el-color-primary-rgb), 0.6);
}

.floating-action-btn:active {
  transform: scale(0.9) rotate(90deg);
}

.custom-scrollbar::-webkit-scrollbar {
  height: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(var(--el-color-primary-rgb), 0.2);
  border-radius: 10px;
}

:deep(.glass-dialog) {
  border-radius: 28px;
  overflow: hidden;
  backdrop-filter: blur(20px);
  background: rgba(255, 255, 255, 0.8);
}

.dark :deep(.glass-dialog) {
  background: rgba(24, 24, 27, 0.9);
}

:deep(.glass-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 24px 24px 10px;
  font-weight: 800;
}

:deep(.glass-dialog .el-dialog__title) {
  font-size: 1.25rem;
  letter-spacing: -0.025em;
}
</style>