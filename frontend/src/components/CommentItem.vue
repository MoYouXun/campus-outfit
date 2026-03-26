<script setup lang="ts">
import { ElAvatar } from 'element-plus'

const props = defineProps<{
  comment: any
  currentUserId?: number
}>()

const emit = defineEmits(['reply', 'delete'])

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours()}:${date.getMinutes()}`
}
</script>

<template>
  <div class="comment-item py-3 border-b border-border/30 last:border-0">
    <div class="flex gap-3">
      <el-avatar :size="32" :src="comment.avatar" class="bg-primary/10 shrink-0" />
      <div class="flex-1 min-w-0">
        <div class="flex justify-between items-start mb-1">
          <div class="text-sm font-bold text-foreground/90">{{ comment.username }}</div>
          <div class="text-[10px] text-muted-foreground">{{ formatDate(comment.createTime) }}</div>
        </div>
        <div class="text-sm text-foreground/80 leading-relaxed mb-2">
          <span v-if="comment.replyToUserName" class="text-primary/70 mr-1">@{{ comment.replyToUserName }}</span>
          {{ comment.content }}
        </div>
        <div class="flex gap-4 items-center">
          <button 
            class="text-[11px] font-medium text-muted-foreground hover:text-primary transition-colors cursor-pointer border-0 bg-transparent p-0"
            @click="emit('reply', comment)"
          >
            回复
          </button>
          <button 
            v-if="comment.userId === currentUserId"
            class="text-[11px] font-medium text-muted-foreground hover:text-destructive transition-colors cursor-pointer border-0 bg-transparent p-0"
            @click="emit('delete', comment.id)"
          >
            删除
          </button>
        </div>
        
        <!-- 子回复列表 -->
        <div v-if="comment.children && comment.children.length > 0" class="mt-3 pl-4 border-l-2 border-primary/10 space-y-2">
          <CommentItem 
            v-for="child in comment.children" 
            :key="child.id" 
            :comment="child" 
            :currentUserId="currentUserId"
            @reply="(c) => emit('reply', c)"
            @delete="(id) => emit('delete', id)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-item {
  animation: fade-in 0.4s ease-out;
}
@keyframes fade-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
