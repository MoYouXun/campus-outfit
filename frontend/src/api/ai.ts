import request from '@/utils/request'

/**
 * 发起两套穿搭 AI PK
 */
export async function pkOutfits(data: { imageAUrl: string; imageBUrl: string; scene: string }) {
  return request.post('/ai/decision/pk', data)
}

/**
 * 发起 AI 试衣生成请求
 */
export async function aiTryOn(data: { humanImageUrl: string; garmentImageUrl: string; category?: string }) {
  return request.post('/ai/try-on/generate', data)
}
