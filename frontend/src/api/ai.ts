import request from '@/utils/request'

/**
 * 发起两套穿搭 AI PK
 */
export async function pkOutfits(data: { imageAUrl: string; imageBUrl: string; scene: string }) {
  return request.post('/ai/decision/pk', data)
}
