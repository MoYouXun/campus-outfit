<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHotRanking, getStyleRanking, getSchoolRanking } from '@/api/ranking'

const router = useRouter()
const activeType = ref('hot')
const activeGender = ref('') // '' 代表全部, 'MALE'男生, 'FEMALE'女生
const rankingList = ref<any[]>([])
const loading = ref(false)

const loadRanking = async () => {
  loading.value = true
  try {
    const params: any = { limit: 20 }
    if (activeGender.value) params.gender = activeGender.value
    
    let res
    if (activeType.value === 'hot') res = await getHotRanking(params) as any
    else if (activeType.value === 'style') res = await getStyleRanking({ ...params, style: '校园' }) as any
    else res = await getSchoolRanking({ ...params, school: '默认校园' }) as any
    
    rankingList.value = res || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadRanking)
</script>

<template>
  <div class="p-6 max-w-5xl mx-auto">
    <h1 class="text-3xl font-bold mb-6">穿搭风向标</h1>

    <div class="flex gap-4 mb-2 overflow-x-auto pb-2">
      <el-button :type="activeType === 'hot' ? 'primary' : 'default'" @click="activeType = 'hot'; loadRanking()" round>热门总榜</el-button>
      <el-button :type="activeType === 'style' ? 'primary' : 'default'" @click="activeType = 'style'; loadRanking()" round>风格榜</el-button>
      <el-button :type="activeType === 'school' ? 'primary' : 'default'" @click="activeType = 'school'; loadRanking()" round>校内榜</el-button>
    </div>

    <div class="flex gap-2 mb-4">
      <el-radio-group v-model="activeGender" @change="loadRanking" size="small">
        <el-radio-button label="">全部性别</el-radio-button>
        <el-radio-button label="MALE">男生热榜</el-radio-button>
        <el-radio-button label="FEMALE">女生热榜</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="space-y-4">
      <div v-for="(item, index) in rankingList" :key="item.id" 
           @click="router.push(`/outfit/${item.id}`)"
           :class="['glass-card p-4 flex items-center gap-4 hover:scale-[1.01] transition-transform cursor-pointer relative overflow-hidden', 
                    index === 0 ? 'border-2 border-yellow-400 shadow-[0_0_15px_rgba(250,204,21,0.3)]' : '',
                    index === 1 ? 'border-2 border-slate-300 shadow-[0_0_15px_rgba(203,213,225,0.3)]' : '',
                    index === 2 ? 'border-2 border-amber-600 shadow-[0_0_15px_rgba(217,119,6,0.2)]' : '']">
        
        <div class="flex flex-col items-center justify-center w-12 shrink-0">
          <div :class="['text-3xl font-black italic', 
                        index === 0 ? 'text-yellow-500' : 
                        index === 1 ? 'text-slate-400' : 
                        index === 2 ? 'text-amber-700' : 'text-primary/30']">
            {{ index === 0 ? '👑' : index + 1 }}
          </div>
          <div class="text-xs mt-1 font-bold">
            <span v-if="item.rankTrend === 999" class="text-orange-500">新</span>
            <span v-else-if="item.rankTrend === 1" class="text-red-500">↑</span>
            <span v-else-if="item.rankTrend === -1" class="text-green-500">↓</span>
            <span v-else class="text-gray-400">-</span>
          </div>
        </div>

        <el-image :src="item.thumbnailUrl || item.imageUrls?.[0]" class="w-20 h-20 rounded-lg object-cover shrink-0" />
        
        <div class="flex-1 min-w-0">
          <h3 class="font-bold text-lg mb-1 truncate">{{ item.title || '无题' }}</h3>
          
          <div class="flex items-center gap-2 mb-2">
             <el-avatar :size="18" :src="item.authorAvatar" class="shrink-0"></el-avatar>
             <span class="text-xs text-gray-500 truncate">{{ item.authorName || '匿名校友' }}</span>
          </div>

          <div class="flex gap-2">
            <el-tag v-for="tag in item.styleTags?.slice(0, 2)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
        </div>

        <div class="text-right shrink-0">
          <div class="text-primary font-bold text-lg">{{ item.likeCount }}</div>
          <div class="text-xs text-muted-foreground uppercase">点赞</div>
        </div>
      </div>
      <el-empty v-if="!loading && rankingList.length === 0" description="榜单还在酝酿中" />
    </div>
  </div>
</template>
