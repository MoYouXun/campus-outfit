<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { User, HotWater, Management, Delete } from '@element-plus/icons-vue'

const activeTab = ref('users')
const overview = ref({ totalUsers: 0, totalOutfits: 0 })

// 用户列表
const users = ref([])
const userPage = ref(1)
const userTotal = ref(0)

// 帖子列表
const outfits = ref([])
const outfitPage = ref(1)
const outfitTotal = ref(0)

const loadUsers = async () => {
  try {
    const res: any = await request.get(`/admin/users?page=${userPage.value}&size=10`)
    users.value = res.records
    userTotal.value = res.total
  } catch (e) {}
}

const loadOutfits = async () => {
  try {
    const res: any = await request.get(`/admin/outfits?page=${outfitPage.value}&size=10`)
    outfits.value = res.records
    outfitTotal.value = res.total
  } catch (e) {}
}

const deleteUser = async (id: number) => {
  await request.delete(`/admin/user/${id}`)
  ElMessage.success('用户已删除')
  loadUsers()
}

const deleteOutfit = async (id: number) => {
  await request.delete(`/admin/outfit/${id}`)
  ElMessage.success('帖子已删除')
  loadOutfits()
}

onMounted(() => {
  loadUsers()
  loadOutfits()
})
</script>

<template>
  <div class="p-8 max-w-7xl mx-auto">
    <div class="flex items-center gap-4 mb-8">
      <div class="w-12 h-12 rounded-2xl bg-primary/10 flex-center text-primary">
        <el-icon size="24"><Management /></el-icon>
      </div>
      <h1 class="text-3xl font-black tracking-tight">管理后台</h1>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10">
      <div class="glass-card p-6 flex items-center gap-6">
        <div class="w-14 h-14 rounded-full bg-blue-500/10 flex-center text-blue-600">
          <el-icon size="28"><User /></el-icon>
        </div>
        <div>
          <div class="text-2xl font-bold">{{ userTotal }}</div>
          <div class="text-sm text-muted-foreground uppercase font-bold tracking-widest">活跃用户</div>
        </div>
      </div>
      <div class="glass-card p-6 flex items-center gap-6">
        <div class="w-14 h-14 rounded-full bg-orange-500/10 flex-center text-orange-600">
          <el-icon size="28"><HotWater /></el-icon>
        </div>
        <div>
          <div class="text-2xl font-bold">{{ outfitTotal }}</div>
          <div class="text-sm text-muted-foreground uppercase font-bold tracking-widest">内容总数</div>
        </div>
      </div>
    </div>

    <div class="glass-card p-6">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="用户库管理" name="users">
          <el-table :data="users" border style="width: 100%" class="mt-4">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="用户">
              <template #default="scope">
                <div class="flex items-center gap-3">
                  <el-avatar :size="24" :src="scope.row.avatar" />
                  <span class="font-bold">{{ scope.row.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="email" label="邮箱" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button type="danger" :icon="Delete" circle @click="deleteUser(scope.row.id)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="mt-6 flex justify-end">
            <el-pagination background layout="prev, pager, next" :total="userTotal" @current-change="(p) => { userPage = p; loadUsers() }" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="内容发布管理" name="outfits">
          <el-table :data="outfits" border style="width: 100%" class="mt-4">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button type="danger" :icon="Delete" circle @click="deleteOutfit(scope.row.id)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="mt-6 flex justify-end">
            <el-pagination background layout="prev, pager, next" :total="outfitTotal" @current-change="(p) => { outfitPage = p; loadOutfits() }" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>
