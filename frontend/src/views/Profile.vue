<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getUserInfo, followUser, unfollowUser } from '@/api/user'
import { getMyOutfits, getUserOutfits, deleteOutfit } from '@/api/outfit'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userId = route.params.id as string
const user = ref<any>(null)
const outfits = ref<any[]>([])
const isFollowing = ref(false)
const userStore = useUserStore()
const isCurrentUser = ref(false)
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)

const loadUser = async () => {
  try {
    const res: any = await getUserInfo(userId, currentUserId.value)
    user.value = res.user
    isFollowing.value = res.following // Jackson 序列化 isFollowing 为 following
    
    // 检查是否是当前用户
    isCurrentUser.value = user.value && currentUserId.value && Number(user.value.id) === Number(currentUserId.value)
    
    // 加载该用户的穿搭：本人展示全部（含私密），他人仅展示公开
    let outfitsRes: any
    if (isCurrentUser.value) {
      outfitsRes = await getMyOutfits({ page: 1, size: 20 })
    } else {
      outfitsRes = await getUserOutfits({ 
        userId: userId,
        page: 1, 
        size: 20 
      })
    }
    outfits.value = outfitsRes.records || []
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (id: number | string) => {
  try {
    await deleteOutfit(id)
    ElMessage.success('帖子删除成功')
    loadUser()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '帖子删除失败')
  }
}

const handleFollow = async () => {
  try {
    if (isFollowing.value) await unfollowUser(userId)
    else await followUser(userId)
    isFollowing.value = !isFollowing.value
    loadUser()
  } catch (e) {}
}

onMounted(loadUser)
</script>

<template>
  <div class="min-h-screen bg-background pb-20">
    <div class="h-48 bg-gradient-to-r from-primary/20 to-secondary/20"></div>
    <div class="max-w-5xl mx-auto px-6 -mt-16">
      <div class="flex flex-col md:flex-row items-center md:items-end gap-6 mb-10">
        <el-avatar :size="120" :src="user?.avatar" class="border-4 border-white shadow-xl" />
        <div class="flex-1 text-center md:text-left">
          <h1 class="text-3xl font-bold mb-2">{{ user?.username }}</h1>
          <p class="text-muted-foreground mb-4">{{ user?.bio || '这家伙很懒，什么都没留下~' }}</p>
          <div class="flex gap-6 justify-center md:justify-start">
            <div class="text-center"><div class="font-bold text-xl">{{ user?.followCount || 0 }}</div><div class="text-xs text-muted-foreground uppercase">关注</div></div>
            <div class="text-center"><div class="font-bold text-xl">{{ user?.fanCount || 0 }}</div><div class="text-xs text-muted-foreground uppercase">粉丝</div></div>
            <div class="text-center"><div class="font-bold text-xl">{{ outfits.length }}</div><div class="text-xs text-muted-foreground uppercase">穿搭</div></div>
          </div>
        </div>
        <div class="pb-2 flex gap-2">
          <el-button v-if="!isCurrentUser" :type="isFollowing ? 'default' : 'primary'" @click="handleFollow" size="large" round>
            {{ isFollowing ? '取消关注' : '加关注' }}
          </el-button>
        </div>
      </div>

      <el-divider content-position="left">发布的穿搭</el-divider>
      <MasonryGallery :outfits="outfits" @delete="handleDelete" />
    </div>
  </div>
</template>
