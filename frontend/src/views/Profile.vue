<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getUserInfo, followUser, unfollowUser } from '@/api/user'
import { getMyOutfits, getUserOutfits, deleteOutfit, getMyPrivateOutfits, updateOutfitStatus, getFavoriteOutfits } from '@/api/outfit'
import { likeOutfit, unlikeOutfit, favoriteOutfit, unfavoriteOutfit } from '@/api/interaction'
import { getWardrobeList, deleteWardrobeItem, uploadWardrobeItem, getWardrobeListByType, getWardrobeListBySeason } from '@/api/wardrobe'
import MasonryGallery from '@/components/MasonryGallery.vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { aiTryOn } from '@/api/ai'
import UploadImage from '@/components/UploadImage.vue'

const route = useRoute()
const userId = route.params.id as string
const user = ref<any>(null)
const outfits = ref<any[]>([]) // 公开列表
const privateOutfits = ref<any[]>([]) // 私人衣橱
const favoriteOutfits = ref<any[]>([]) // 我的收藏
const isFollowing = ref(false)
const userStore = useUserStore()
const isCurrentUser = ref(false)
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.userId || null)
const activeTab = ref('public')

// 衣柜状态
const wardrobeItems = ref<any[]>([])
const wardrobeLoading = ref(false)
const showUploadDialog = ref(false)
const uploading = ref(false)
const wardrobeFilter = ref({
  type: '',
  season: ''
})
const wardrobeForm = ref({
  type: '上装',
  color: '白色',
  style: '休闲',
  season: '春季',
  file: null as File | null
})

const typeOptions = ['上装', '下装', '套装', '鞋靴', '配饰']
const seasonOptions = ['春季', '夏季', '秋季', '冬季', '四季']
const styleOptions = ['休闲', '正式', '运动', '极简', '复古', '甜美', '机能']

const loadWardrobe = async () => {
  if (!isCurrentUser.value) return
  wardrobeLoading.value = true
  try {
    let res: any
    if (wardrobeFilter.value.type) {
      res = await getWardrobeListByType(wardrobeFilter.value.type)
    } else if (wardrobeFilter.value.season) {
      res = await getWardrobeListBySeason(wardrobeFilter.value.season)
    } else {
      res = await getWardrobeList()
    }
    wardrobeItems.value = res.data || []
  } catch (e) {
    console.error('加载衣柜失败:', e)
  } finally {
    wardrobeLoading.value = false
  }
}

const handleUploadWardrobe = async () => {
  if (!wardrobeForm.value.file) {
    ElMessage.warning('请先选择或拍摄单品图片')
    return
  }
  uploading.value = true
  try {
    const { file, ...metadata } = wardrobeForm.value
    await uploadWardrobeItem(file, metadata)
    ElMessage.success('单品已存入电子衣橱')
    showUploadDialog.value = false
    loadWardrobe()
    // 重置表单
    wardrobeForm.value.file = null
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDeleteWardrobe = async (id: number | string) => {
  try {
    await ElMessageBox.confirm('确定要从衣柜中永久删除该单品吗？此操作不可撤销。', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'glass-message-box'
    })
    await deleteWardrobeItem(id)
    ElMessage.success('单品已移除')
    wardrobeItems.value = wardrobeItems.value.filter(item => item.id !== id)
  } catch (e) {}
}

const onWardrobeFileChange = (data: any) => {
  wardrobeForm.value.file = data.compressedFile || data.originalFile
}

const loadUser = async () => {
  try {
    const res: any = await getUserInfo(userId, currentUserId.value)
    user.value = res.user
    isFollowing.value = res.following
    
    isCurrentUser.value = user.value && currentUserId.value && Number(user.value.id) === Number(currentUserId.value)
    
    let outfitsRes: any
    if (isCurrentUser.value) {
      outfitsRes = await getMyOutfits({ page: 1, size: 20 })
      const privateRes: any = await getMyPrivateOutfits()
      privateOutfits.value = privateRes || []
      const favoriteRes: any = await getFavoriteOutfits()
      favoriteOutfits.value = favoriteRes || []
    } else {
      outfitsRes = await getUserOutfits({ 
        userId: userId,
        page: 1, 
        size: 20 
      })
    }
    outfits.value = outfitsRes.records || []
    
    // 如果是当前用户，加载衣柜
    if (isCurrentUser.value) {
      loadWardrobe()
    }
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

const handleToggleStatus = async (item: any, newStatus: string) => {
  const isTargetPrivate = newStatus === 'PRIVATE'
  const actionText = isTargetPrivate ? '移入私人衣橱' : '发布到社区'
  
  try {
    await ElMessageBox.confirm(`确定要将该穿搭${actionText}吗？`, '状态流转', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
      customClass: 'glass-message-box'
    })
    
    await updateOutfitStatus(item.id, newStatus as any)
    ElMessage.success(`${actionText}成功`)
    
    // 本地模型状态更新，确保渲染一致
    item.status = newStatus
    
    if (isTargetPrivate) {
      // 从公开列表移除，加入私人列表
      const idx = outfits.value.findIndex(o => o.id === item.id)
      if (idx !== -1) outfits.value.splice(idx, 1)
      privateOutfits.value.unshift(item)
    } else {
      // 从私人列表移除，加入公开列表
      const idx = privateOutfits.value.findIndex(o => o.id === item.id)
      if (idx !== -1) privateOutfits.value.splice(idx, 1)
      outfits.value.unshift(item)
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

const handleLike = async (item: any) => {
  if (!userStore.token) {
    ElMessage.warning('请登录后再参与互动')
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
    return
  }
  try {
    if (item.favorited) {
      await unfavoriteOutfit(item.id, currentUserId.value!)
      item.favCount = Math.max(0, (item.favCount || 0) - 1)
      item.favorited = false
      if (activeTab.value === 'favorites') {
        favoriteOutfits.value = favoriteOutfits.value.filter(o => o.id !== item.id)
      }
    } else {
      await favoriteOutfit(item.id, currentUserId.value!)
      item.favCount = (item.favCount || 0) + 1
      item.favorited = true
      ElMessage({ message: '收藏成功！', type: 'success', grouping: true })
      if (activeTab.value !== 'favorites') {
        const exist = favoriteOutfits.value.find(o => o.id === item.id)
        if (!exist) {
           favoriteOutfits.value.unshift({...item})
        }
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
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

const showTryOnDialog = ref(false)
const tryOnLoading = ref(false)
const personImageUrl = ref('') // 空图，待上传
const selectedOutfitUrl = ref('')
const tryOnResultUrl = ref('')

const handleAvatarUploadSuccess = (data: any) => {
  personImageUrl.value = data.base64Data
}

const handleTryOn = async () => {
  if (!personImageUrl.value || !selectedOutfitUrl.value) {
    ElMessage.warning('请提供人像照片并选择一件衣服')
    return
  }
  tryOnLoading.value = true
  tryOnResultUrl.value = ''
  try {
    const res: any = await aiTryOn({
      humanImageUrl: personImageUrl.value,
      garmentImageUrl: selectedOutfitUrl.value
    })
    tryOnResultUrl.value = res.data || res || personImageUrl.value
    ElMessage.success('魔法换装成功！')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '换装请求失败')
  } finally {
    tryOnLoading.value = false
  }
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
          <el-button v-if="isCurrentUser" type="primary" :icon="MagicStick" @click="showTryOnDialog = true" plain round>
            AI 换装室
          </el-button>
          <el-button v-if="!isCurrentUser" :type="isFollowing ? 'default' : 'primary'" @click="handleFollow" size="large" round>
            {{ isFollowing ? '取消关注' : '加关注' }}
          </el-button>
        </div>
      </div>

      <div class="mt-8">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <el-tab-pane label="公开穿搭" name="public">
            <MasonryGallery :outfits="outfits" @delete="handleDelete" @toggle-status="(item) => handleToggleStatus(item, 'PRIVATE')" @like="handleLike" @favorite="handleFavorite" />
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="私人衣橱" name="private">
            <div class="pt-2">
              <el-alert title="这是您的私密空间，此页签下所有内容仅您本人可见。" type="warning" show-icon class="mb-6" :closable="false" />
              <MasonryGallery :outfits="privateOutfits" @delete="handleDelete" @toggle-status="(item) => handleToggleStatus(item, 'PUBLISHED')" @like="handleLike" @favorite="handleFavorite" />
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="我的收藏" name="favorites">
            <div class="pt-2">
              <MasonryGallery :outfits="favoriteOutfits" @delete="handleDelete" @like="handleLike" @favorite="handleFavorite" :hideManagementButtons="true" />
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="isCurrentUser" label="我的衣柜" name="wardrobe">
            <div class="pt-4">
              <!-- 控制栏 -->
              <div class="flex flex-wrap gap-4 items-center justify-between mb-8 pb-4 border-b border-border/40">
                <div class="flex gap-4 items-center">
                  <el-select v-model="wardrobeFilter.type" placeholder="全部类型" clearable @change="loadWardrobe" class="w-32">
                    <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-select v-model="wardrobeFilter.season" placeholder="全部季节" clearable @change="loadWardrobe" class="w-32">
                    <el-option v-for="s in seasonOptions" :key="s" :label="s" :value="s" />
                  </el-select>
                </div>
                <el-button type="primary" :icon="Plus" @click="showUploadDialog = true" round shadow>
                  上传单品到衣柜
                </el-button>
              </div>

              <!-- 衣柜网格 -->
              <div v-loading="wardrobeLoading">
                <div v-if="!wardrobeItems || wardrobeItems.length === 0" class="flex-center py-20 opacity-50 flex-col">
                  <el-icon class="text-5xl mb-4 text-muted-foreground"><Picture /></el-icon>
                  <p class="text-muted-foreground">衣柜空空如也，快去上传您的时尚单品吧~</p>
                </div>

                <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
                  <div 
                    v-for="item in wardrobeItems" 
                    :key="item.id" 
                    class="glass-card group relative p-3 transition-all duration-500 hover:-translate-y-1 hover:shadow-2xl overflow-hidden"
                  >
                    <!-- 图片容器 -->
                    <div class="relative aspect-[3/4] rounded-xl overflow-hidden mb-3">
                      <el-image 
                        :src="item.originalImageUrl" 
                        fit="cover" 
                        class="w-full h-full transition-transform duration-700 group-hover:scale-110"
                        :preview-src-list="[item.originalImageUrl]"
                      />
                      
                      <!-- 悬浮删除操作 -->
                      <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-3">
                        <el-button type="danger" :icon="Delete" circle @click.stop="handleDeleteWardrobe(item.id)" />
                      </div>

                      <!-- 类别/季节浮窗 -->
                      <div class="absolute top-2 left-2 flex flex-col gap-1">
                        <span class="px-2 py-0.5 text-[10px] bg-black/60 backdrop-blur-md text-white rounded-full border border-white/10">
                          {{ item.categoryMain }}
                        </span>
                        <span v-if="item.season" class="px-2 py-0.5 text-[10px] bg-primary/80 backdrop-blur-md text-white rounded-full border border-white/10">
                          {{ item.season }}
                        </span>
                      </div>
                    </div>

                    <!-- 单品描述 -->
                    <div class="px-1">
                      <div class="flex items-center justify-between mb-1">
                        <span class="text-xs font-bold truncate opacity-80">{{ item.style || '默认风格' }}</span>
                        <div class="w-3 h-3 rounded-full border border-border/50" :style="{ backgroundColor: item.color === '黑色' ? '#000' : item.color === '白色' ? '#fff' : '#ccc' }" :title="item.color"></div>
                      </div>
                      <p class="text-[10px] text-muted-foreground line-clamp-1 italic">{{ item.createTime || '最近上传' }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 衣柜单品上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="🪄 将单品存入电子衣橱" width="500px" append-to-body class="glass-dialog" destroy-on-close>
      <div v-loading="uploading" element-loading-text="正在为您整理衣橱..." class="space-y-6 p-2">
        <UploadImage @upload-success="onWardrobeFileChange" />
        
        <el-form label-position="top">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="单品类型">
                <el-select v-model="wardrobeForm.type" class="w-full">
                  <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="适合季节">
                <el-select v-model="wardrobeForm.season" class="w-full">
                  <el-option v-for="s in seasonOptions" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="主色调">
                <el-input v-model="wardrobeForm.color" placeholder="例如：月牙白" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服饰风格">
                <el-select v-model="wardrobeForm.style" class="w-full">
                  <el-option v-for="st in styleOptions" :key="st" :label="st" :value="st" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="pt-4 flex justify-end gap-3">
          <el-button @click="showUploadDialog = false" round>取消</el-button>
          <el-button type="primary" @click="handleUploadWardrobe" :loading="uploading" round class="px-8 shadow-lg shadow-primary/20">
            确认存入衣橱
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- AI 换装室弹窗 -->
    <el-dialog v-model="showTryOnDialog" title="🪄 私人 AI 换装室" width="600px" destroy-on-close>
      <div v-loading="tryOnLoading" element-loading-text="AI 正在为您施展换装魔法..." class="p-4">
        <el-alert title="作为概念原型，目前换装功能基于图生图接口，提供效果预览。" type="info" show-icon class="mb-4" :closable="false"/>
        
        <div class="mb-4">
          <div class="text-sm font-bold mb-3 text-foreground/80 flex items-center gap-2">1. 个人基础照 (人像)</div>
          
          <!-- 完全复用首页炫酷上传组件 -->
          <div v-if="!personImageUrl" class="scale-90 origin-top">
            <UploadImage @upload-success="handleAvatarUploadSuccess" />
          </div>
          
          <div v-else class="relative w-40 h-56 rounded-xl mx-auto border border-border shadow-sm overflow-hidden bg-secondary/30 group">
            <img :src="personImageUrl" class="w-full h-full object-cover" />
            <div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white text-sm gap-1 font-medium cursor-pointer" @click="personImageUrl = ''">
              <el-icon><Refresh /></el-icon> 更换照片
            </div>
          </div>
        </div>

        <div class="mb-6">
          <div class="text-sm font-bold mb-2 text-foreground/80">2. 选择衣橱服饰 (<span class="text-primary italic">将衣服穿在底图上</span>)</div>
          <el-select v-model="selectedOutfitUrl" placeholder="从您发布的穿搭中选择一件上装或下装..." class="w-full" size="large">
            <el-option
              v-for="outfit in outfits"
              :key="outfit.id"
              :label="outfit.title"
              :value="outfit.thumbnailUrl || outfit.imageUrls?.[0]"
            >
              <div class="flex items-center gap-3">
                <img :src="outfit.thumbnailUrl || outfit.imageUrls?.[0]" class="w-8 h-8 rounded-md object-cover shadow-sm border border-border" />
                <span class="font-medium">{{ outfit.title }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="text-center mt-8">
          <el-button type="primary" :icon="MagicStick" @click="handleTryOn" :disabled="!personImageUrl || !selectedOutfitUrl" class="w-full h-12 text-lg rounded-xl shadow-lg shadow-primary/30 transition-transform hover:-translate-y-1">
            一 键 换 装
          </el-button>
        </div>

        <div v-if="tryOnResultUrl" class="mt-8 animate-fade-in">
          <el-divider>✨ 魔法换装结果 ✨</el-divider>
          <div class="w-full rounded-2xl overflow-hidden shadow-2xl border border-primary/30 p-2 bg-gradient-to-br from-primary/10 via-background to-secondary/10">
            <el-image :src="tryOnResultUrl" class="w-full rounded-xl mix-blend-multiply dark:mix-blend-normal" :preview-src-list="[tryOnResultUrl]" fit="contain" />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
