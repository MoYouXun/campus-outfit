<script setup lang="ts">
import AiAnalysisCard from '@/components/AiAnalysisCard.vue'
</script>

<template>
  <div class="min-h-screen bg-background/50 flex flex-col items-center pt-8 pb-20 px-4 sm:px-6 lg:px-8">
    
    <!-- Hero Section -->
    <div class="text-center max-w-3xl mx-auto mb-12 animate-fade-in relative px-4">
      <div class="absolute -top-6 -left-10 w-20 h-20 bg-primary/20 rounded-full blur-2xl"></div>
      <h1 class="text-4xl sm:text-5xl font-extrabold tracking-tight text-foreground mb-4">
        AI <span class="text-gradient">校园穿搭</span> 智能助手
      </h1>
      <p class="text-lg text-muted-foreground">
        不再为每天“穿什么”而烦恼。利用领先的 AI 多模态大模型结合分析实时天气，智能解析衣橱并为您推荐最匹配的个性穿搭策略。
      </p>
    </div>

    <!-- AI 分析核心组件容器 -->
    <div class="w-full flex justify-center animate-slide-up">
      <AiAnalysisCard />
    </div>
    
  </div>
</template>

<style scoped>
.text-gradient {
  background: linear-gradient(135deg, var(--el-color-primary) 0%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fade-in 0.8s ease-out;
}

.animate-slide-up {
  animation: slide-up 0.8s ease-out 0.2s both;
}
</style>