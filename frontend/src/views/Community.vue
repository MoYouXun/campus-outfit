<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import MasonryGallery from '../components/MasonryGallery.vue'
import { ElMessage, ElScrollbar, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getCommunityFeed, getHotTopics, getFollowingFeed } from '../api/community'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '../api/interaction'
import { deleteOutfit } from '../api/outfit'
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
        />
      </el-scrollbar>

    </div>
  </div>
</template>