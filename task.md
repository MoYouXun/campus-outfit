# 推荐模块完善 — 任务拆解

## 1. 后端：修复温度解析崩溃隐患 [高危]
- [x] [RecommendServiceImpl.java](file:///e:/Profession/specialty%20practice2/campus-outfit-project/backend/src/main/java/com/campus/outfit/service/impl/RecommendServiceImpl.java) - [recommendBySeason](file:///e:/Profession/specialty%20practice2/campus-outfit-project/backend/src/main/java/com/campus/outfit/service/impl/RecommendServiceImpl.java#38-100)：用正则代替 `Integer.parseInt` 解析温度（已确认实现）

## 2. 后端：修复 AI 私服推荐越权漏洞 [安全]
- [x] [RecommendServiceImpl.java](file:///e:/Profession/specialty%20practice2/campus-outfit-project/backend/src/main/java/com/campus/outfit/service/impl/RecommendServiceImpl.java) - [recommendPersonalized](file:///e:/Profession/specialty%20practice2/campus-outfit-project/backend/src/main/java/com/campus/outfit/service/impl/RecommendServiceImpl.java#182-273)：限制 AI 返回的 ID 只能来自当前用户的衣橱（已确认实现）
- [x] 限制 AI 返回的 `searchTags` 最多 3 个，避免慢 SQL（已确认实现）

## 3. 前端：补全分页/无限滚动加载 [功能]
- [x] [Recommend.vue](file:///e:/Profession/specialty%20practice2/campus-outfit-project/frontend/src/views/Recommend.vue) - 追加 `currentPage` & `hasMore` & `loadingMore` 状态
- [x] 底部增加 `v-infinite-scroll` 触发 `loadMore` 函数
- [x] 每次加载后 `push` 到 `outfits.value` 而非覆盖

## 4. 前端：AI 推理"打字机"动画效果 [体验]
- [x] [Recommend.vue](file:///e:/Profession/specialty%20practice2/campus-outfit-project/frontend/src/views/Recommend.vue) - 使用 `displayedReasoning` + `setInterval` 逐字打出文字
- [x] 引入 `typing-cursor` 光标 CSS 闪烁动画

## 5. 文案：调整推荐理由标签的展示位置与文字
- [x] [MasonryGallery.vue](file:///e:/Profession/specialty%20practice2/campus-outfit-project/frontend/src/components/MasonryGallery.vue) - 推荐理由标签改为 `truncate` 单行截断，限制 max-w-[85%]
