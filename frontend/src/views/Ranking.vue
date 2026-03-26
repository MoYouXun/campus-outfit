<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHotRanking, getStyleRanking, getSchoolRanking } from '@/api/ranking'

const activeType = ref('hot')
const rankingList = ref([])
const loading = ref(false)

const loadRanking = async () => {
  loading.value = true
  try {
    let res
    if (activeType.value === 'hot') res = await getHotRanking({ limit: 20 })
    else if (activeType.value === 'style') res = await getStyleRanking({ style: '校园', limit: 20 })
    else res = await getSchoolRanking({ school: '默认校园', limit: 20 })
    
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

    <div class="flex gap-4 mb-8 overflow-x-auto pb-2">
      <el-button :type="activeType === 'hot' ? 'primary' : 'default'" @click="activeType = 'hot'; loadRanking()" round>热门总榜</el-button>
      <el-button :type="activeType === 'style' ? 'primary' : 'default'" @click="activeType = 'style'; loadRanking()" round>风格榜</el-button>
      <el-button :type="activeType === 'school' ? 'primary' : 'default'" @click="activeType = 'school'; loadRanking()" round>校内榜</el-button>
    </div>

    <div v-loading="loading" class="space-y-4">
      <div v-for="(item, index) in rankingList" :key="item.id" class="glass-card p-4 flex items-center gap-6 hover:scale-[1.01] transition-transform cursor-pointer">
        <div class="text-3xl font-black italic text-primary/30 w-12">{{ index + 1 }}</div>
        <el-image :src="item.thumbnailUrl || item.imageUrls?.[0]" class="w-20 h-20 rounded-lg object-cover" />
        <div class="flex-1">
          <h3 class="font-bold text-lg mb-1">{{ item.title || '无题' }}</h3>
          <div class="flex gap-2">
            <el-tag v-for="tag in item.styleTags?.slice(0, 2)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
        </div>
        <div class="text-right">
          <div class="text-primary font-bold">{{ item.likeCount }}</div>
          <div class="text-xs text-muted-foreground uppercase">点赞</div>
        </div>
      </div>
      <el-empty v-if="!loading && rankingList.length === 0" description="榜单还在酝酿中" />
    </div>
  </div>
</template>
