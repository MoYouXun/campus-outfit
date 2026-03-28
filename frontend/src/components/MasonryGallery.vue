<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Star, StarFilled, Picture, Delete, Pointer, MagicStick, Lock } from '@element-plus/icons-vue'
import { getImageUrl } from '../api/image'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  outfits: any[]
}>()

const router = useRouter()
const emit = defineEmits(['like', 'favorite', 'delete'])

// 获取当前登录用户信息
const userStore = useUserStore()

// 临时测试用户（模拟登录状态）
// const testUserId = 8 // 与帖子的用户ID匹配

// 检查当前登录用户是否是帖子所有者
const isPostOwner = (item: any): boolean => {
  const currentId = userStore.userInfo?.id || userStore.userInfo?.userId || null
  // 将 ID 统一转为 String 进行比较，避免 Number/String 类型混用导致的判断失效
  return !!currentId && !!item.userId && String(item.userId) === String(currentId)
}

// 存储刷新后的URL，避免重复刷新
const refreshedUrls = ref(new Map<string, string>())

// 从URL中提取对象名
function extractObjectName(url: string): string | null {
  try {
    const path = url.split('?')[0]
    const segments = path.split('/')
    return segments[segments.length - 1] || null
  } catch (error) {
    console.error('解析URL失败:', error)
    return null
  }
}

// 处理图片加载失败，尝试刷新URL
async function handleImageError(event: Event, item: any, index: number = 0) {
  const img = event.target as HTMLImageElement
  const currentUrl = img.src
  
  // 如果已经尝试刷新过，不再重试
  if (refreshedUrls.value.has(currentUrl)) {
    console.warn('图片已尝试刷新，不再重试:', currentUrl)
    return
  }
  
  try {
    console.log('图片加载失败，尝试刷新URL:', currentUrl)
    
    // 从当前URL中提取对象名
    const objectName = extractObjectName(currentUrl)
    if (!objectName) {
      console.error('无法从URL中提取对象名:', currentUrl)
      return
    }
    
    // 调用API获取新的URL
    const response = await getImageUrl(objectName)
    const newUrl = response.data
    
    if (newUrl) {
      // 标记此URL已刷新
      refreshedUrls.value.set(currentUrl, newUrl)
      
      // 更新图片的src
      img.src = newUrl
      
      // 更新数据源中的URL，确保后续使用新URL
      if (item.thumbnailUrl === currentUrl) {
        item.thumbnailUrl = newUrl
      }
      if (item.imageUrls && item.imageUrls[index] === currentUrl) {
        item.imageUrls[index] = newUrl
      }
      
      console.log('图片URL刷新成功:', newUrl)
    }
  } catch (error) {
    console.error('图片URL刷新失败:', error)
  }
}

const handleItemClick = (item: any) => {
  router.push(`/outfit/${item.id}`)
}
</script>

<template>
  <div class="w-full">
    <div v-if="!outfits || outfits.length === 0" class="flex-center py-20 opacity-50">
      <div class="text-center">
        <el-icon class="text-5xl mb-4 text-muted-foreground"><Picture /></el-icon>
        <p class="text-muted-foreground">暂无展示的穿搭数据哦~</p>
      </div>
    </div>

    <div v-else class="masonry-container">
      <div 
        v-for="item in outfits" 
        :key="item.id" 
        class="masonry-item animate-slide-up hover:-translate-y-1 transition-transform duration-300 ease-out"
        @click="handleItemClick(item)"
      >
        <div class="glass border border-border/40 rounded-2xl overflow-hidden cursor-pointer group relative shadow-md hover:shadow-xl transition-all h-full flex flex-col">
          <div class="relative overflow-hidden w-full">
            <img 
              :src="item.thumbnailUrl || (item.imageUrls && item.imageUrls[0]) || 'https://via.placeholder.com/400x600?text=No+Image'" 
              loading="lazy" 
              class="w-full h-auto object-cover group-hover:scale-105 transition-transform duration-700 ease-in-out" 
              alt="Outfit Image"
              @error="handleImageError($event, item)"
            />
            
            <!-- 私密标识（常驻） -->
            <div v-if="item.status === 'PRIVATE'" class="absolute top-3 left-3 z-10 pointer-events-none">
              <span class="px-2 py-0.5 text-[10px] font-black rounded-full bg-black/60 backdrop-blur-md text-white border border-white/20 shadow-lg flex items-center gap-1">
                <el-icon><Lock /></el-icon> 私密
              </span>
            </div>
            
            <div class="absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300">
              <!-- 删除按钮 -->
              <div v-if="isPostOwner(item)" class="absolute top-4 right-4 animate-fade-in" @click.stop="emit('delete', item.id)">
                <div class="w-10 h-10 rounded-full bg-red-500/80 backdrop-blur-md flex-center text-white hover:bg-red-600 transition-colors">
                  <el-icon size="20"><Delete /></el-icon>
                </div>
              </div>
              <!-- 交互按钮组 -->
              <div class="absolute bottom-4 right-4 flex gap-2 animate-fade-in">
                <!-- 点赞按钮 -->
                <div 
                  class="w-10 h-10 rounded-full backdrop-blur-md flex-center transition-all duration-300"
                  :class="item.liked ? 'bg-red-500 text-white' : 'bg-white/20 text-white hover:bg-white/40'"
                  @click.stop="emit('like', item)"
                >
                  <el-icon size="20"><Pointer /></el-icon>
                </div>
                <!-- 收藏按钮 -->
                <div 
                  class="w-10 h-10 rounded-full backdrop-blur-md flex-center transition-all duration-300"
                  :class="item.favorited ? 'bg-yellow-500 text-white' : 'bg-white/20 text-white hover:bg-white/40'"
                  @click.stop="emit('favorite', item)"
                >
                  <el-icon size="20">
                    <component :is="item.favorited ? StarFilled : Star" />
                  </el-icon>
                </div>
              </div>
            </div>
            
            <div v-if="item.recommendReason" class="absolute top-3 left-3 z-10 max-w-[85%]">
              <span class="px-2 py-0.5 text-[10px] rounded-md bg-yellow-400/90 text-yellow-900 border border-yellow-500/30 font-bold shadow-sm backdrop-blur-sm block truncate">
                ✨ {{ item.recommendReason }}
              </span>
            </div>

            <!-- 匹配度分数与标签 -->
            <div v-if="item.matchScore" class="absolute top-10 left-3 z-10 flex flex-col gap-1.5 animate-fade-in">
              <span class="px-2.5 py-1 text-[11px] font-black rounded-full bg-gradient-to-r from-primary to-primary-light text-white border border-white/30 shadow-lg backdrop-blur-md flex items-center gap-1 w-fit">
                <el-icon><MagicStick /></el-icon>
                匹配度 {{ item.matchScore }}%
              </span>
              <div class="flex gap-1">
                <span v-for="lb in item.matchLabels" :key="lb" class="px-1.5 py-0.5 text-[9px] font-bold rounded bg-black/40 text-white backdrop-blur-md border border-white/10">
                  {{ lb }}
                </span>
              </div>
            </div>
            
            <div v-if="item.season" class="absolute top-3 right-3 shrink-0 z-10">
              <span class="px-2.5 py-1 text-xs font-semibold rounded-full bg-black/60 backdrop-blur-md text-white border border-white/20 shadow-sm">
                {{ item.season }}
              </span>
            </div>
          </div>

          <div class="p-4 flex-1 flex flex-col justify-between gap-3 bg-background/50">
            <div class="font-bold text-sm line-clamp-1">{{ item.title || '校友的精彩穿搭' }}</div>
            <div class="flex flex-wrap gap-1.5">
              <span 
                v-for="(tag, index) in (item.styleTags?.slice(0, 2) || [])" 
                :key="index"
                class="px-2 py-0.5 text-[11px] rounded-md bg-secondary text-secondary-foreground border border-border/50 font-medium"
              >
                # {{ tag }}
              </span>
            </div>
            
            <div class="flex justify-between items-center mt-auto">
              <div class="flex items-center gap-2">
                <el-avatar :size="24" :src="item.userAvatar" class="bg-primary/20" />
                <span class="text-xs font-medium text-foreground/80 truncate max-w-[80px]">{{ item.username || '匿名用户' }}</span>
              </div>
              <div class="flex items-center gap-1 text-muted-foreground text-[10px] font-medium transition-colors" :class="{'text-yellow-500': item.favorited}">
                <el-icon><component :is="item.favorited ? StarFilled : Star" /></el-icon>
                <span>{{ item.favCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
