<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHotRanking } from '@/api/ranking'
import { Trophy, TrendCharts, Male, Female, Timer, HotWater, Star, CaretTop, CaretBottom, Minus } from '@element-plus/icons-vue'

const router = useRouter()
const activeGender = ref('') // '' 代表全部, 'MALE'男生, 'FEMALE'女生
const rankingList = ref<any[]>([])
const loading = ref(false)

const loadRanking = async () => {
  loading.value = true
  try {
    const params: any = { limit: 20 }
    if (activeGender.value) params.gender = activeGender.value
    
    const res = await getHotRanking(params) as any
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
  <div class="ranking-container min-h-screen relative overflow-hidden pb-20">
    <!-- 背景装饰 -->
    <div class="absolute -top-24 -right-24 w-96 h-96 bg-primary/5 rounded-full blur-3xl -z-10"></div>
    <div class="absolute top-1/2 -left-24 w-72 h-72 bg-accent/5 rounded-full blur-3xl -z-10"></div>

    <div class="max-w-5xl mx-auto px-6 pt-12">
      <!-- 头部 -->
      <div class="text-center mb-12 animate-fade-in">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-black uppercase tracking-widest mb-4">
          <el-icon><HotWater /></el-icon> Trending Now
        </div>
        <h1 class="text-5xl font-black text-foreground tracking-tighter mb-4 flex items-center justify-center gap-3">
          穿搭<span class="text-primary italic">风向标</span>
        </h1>
        <p class="text-muted-foreground text-lg max-w-lg mx-auto">实时洞察校园潮流脉搏，发现最受欢迎的穿搭灵感</p>
      </div>

      <!-- 过滤器 -->
      <div class="flex justify-center mb-10 animate-slide-up">
        <div class="bg-secondary/30 backdrop-blur-md p-1.5 rounded-2xl border border-white/20 inline-flex shadow-inner">
          <button 
            v-for="opt in [{label: '全站', value: ''}, {label: '男生', value: 'MALE', icon: Male}, {label: '女生', value: 'FEMALE', icon: Female}]"
            :key="opt.value"
            @click="activeGender = opt.value; loadRanking()"
            :class="['px-6 py-2 rounded-xl text-sm font-bold transition-all flex items-center gap-2', 
                     activeGender === opt.value ? 'bg-primary text-white shadow-lg shadow-primary/30' : 'text-muted-foreground hover:bg-white/50 dark:hover:bg-black/20']"
          >
            <el-icon v-if="opt.icon"><component :is="opt.icon" /></el-icon>
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- 榜单列表 -->
      <div v-loading="loading" class="space-y-6">
        <div v-for="(item, index) in rankingList" :key="item.id" 
             @click="router.push(`/outfit/${item.id}`)"
             class="rank-item group animate-list-in"
             :style="{ '--delay': index * 0.05 + 's' }">
          
          <div :class="['glass-card p-5 flex items-center gap-6 cursor-pointer relative overflow-hidden transition-all duration-500', 
                        index < 3 ? 'border-primary/20 shadow-xl' : 'hover:border-primary/30']">
            
            <!-- 背景奖章水印 (仅前三) -->
            <div v-if="index < 3" class="absolute -right-4 -bottom-4 opacity-[0.03] rotate-12 group-hover:rotate-0 transition-transform duration-700 pointer-events-none">
              <el-icon :size="160"><Trophy /></el-icon>
            </div>

            <!-- 排名与趋势 -->
            <div class="flex flex-col items-center justify-center w-14 shrink-0 relative">
              <div v-if="index < 3" class="rank-badge" :class="`rank-${index + 1}`">
                {{ index + 1 }}
              </div>
              <div v-else class="text-2xl font-black italic text-foreground/20 group-hover:text-primary/40 transition-colors">
                {{ index + 1 }}
              </div>
              
              <div class="mt-2 flex items-center gap-0.5">
                <template v-if="item.rankTrend === 999">
                  <span class="text-[10px] font-black px-1.5 py-0.5 rounded-full bg-orange-500/10 text-orange-500 uppercase">New</span>
                </template>
                <template v-else-if="item.rankTrend === 1">
                  <el-icon class="text-red-500 animate-bounce-y" size="12"><CaretTop /></el-icon>
                </template>
                <template v-else-if="item.rankTrend === -1">
                  <el-icon class="text-green-500 animate-bounce-y-reverse" size="12"><CaretBottom /></el-icon>
                </template>
                <template v-else>
                  <el-icon class="text-gray-300" size="10"><Minus /></el-icon>
                </template>
              </div>
            </div>

            <!-- 图片 -->
            <div class="relative shrink-0 overflow-hidden rounded-2xl group-hover:shadow-2xl group-hover:shadow-primary/20 transition-all duration-500">
              <el-image :src="item.thumbnailUrl || item.imageUrls?.[0]" class="w-24 h-24 object-cover transform group-hover:scale-110 transition-transform duration-700" />
              <div class="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
            </div>
            
            <!-- 内容 -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1.5">
                <h3 class="font-black text-xl truncate group-hover:text-primary transition-colors tracking-tight">{{ item.title || '无题穿搭' }}</h3>
                <el-tooltip content="高热度推荐" placement="top">
                  <el-icon v-if="index < 5" class="text-orange-500"><HotWater /></el-icon>
                </el-tooltip>
              </div>
              
              <div class="flex items-center gap-3 mb-3">
                 <div class="flex items-center gap-1.5">
                   <el-avatar :size="20" :src="item.authorAvatar" class="border border-white shadow-sm shrink-0"></el-avatar>
                   <span class="text-xs font-bold text-muted-foreground">{{ item.authorName || '匿名校友' }}</span>
                 </div>
                 <div class="w-1 h-1 rounded-full bg-border"></div>
                 <div class="flex gap-1.5">
                   <el-tag v-for="tag in item.styleTags?.slice(0, 2)" :key="tag" 
                           class="premium-tag">{{ tag }}</el-tag>
                 </div>
              </div>

              <!-- 统计 -->
              <div class="flex items-center gap-4">
                <div class="flex items-center gap-1 text-xs font-black text-primary">
                  <el-icon><HotWater /></el-icon>
                  {{ Math.round(item.likeCount * 1.5 + 100) }} 热度
                </div>
                <div class="flex items-center gap-1 text-xs font-bold text-muted-foreground">
                  <el-icon><Star /></el-icon>
                  {{ item.likeCount }}
                </div>
              </div>
            </div>

            <!-- 勋章/交互 -->
            <div class="flex flex-col items-end gap-2 shrink-0 pr-2">
              <el-button circle plain type="primary" class="opacity-0 group-hover:opacity-100 translate-x-4 group-hover:translate-x-0 transition-all duration-500">
                <el-icon><TrendCharts /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && rankingList.length === 0" description="榜单还在酝酿中" class="glass-card py-20" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-card {
  @apply bg-background/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-3xl shadow-[0_8px_32px_rgba(0,0,0,0.05)] hover:shadow-[0_12px_48px_rgba(0,0,0,0.1)];
}

.rank-badge {
  @apply w-10 h-10 rounded-xl flex items-center justify-center font-black italic text-xl shadow-lg transform -rotate-12 group-hover:rotate-0 transition-transform duration-500;
}

.rank-1 { @apply bg-gradient-to-br from-yellow-300 to-yellow-600 text-white shadow-yellow-500/30; }
.rank-2 { @apply bg-gradient-to-br from-slate-300 to-slate-500 text-white shadow-slate-400/30; }
.rank-3 { @apply bg-gradient-to-br from-amber-500 to-amber-800 text-white shadow-amber-700/30; }

.premium-tag {
  @apply bg-secondary/50 border-none text-[10px] font-black uppercase tracking-widest px-2 py-0.5 rounded-md text-foreground/60;
}

.animate-fade-in { animation: fadeIn 0.8s ease-out forwards; }
.animate-slide-up { animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards; }

.animate-list-in {
  opacity: 0;
  transform: translateY(20px);
  animation: listIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  animation-delay: var(--delay);
}

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideUp { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }
@keyframes listIn { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }

@keyframes bounce-y {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
.animate-bounce-y { animation: bounce-y 1s infinite ease-in-out; }
.animate-bounce-y-reverse { animation: bounce-y 1s infinite ease-in-out reverse; }
</style>
