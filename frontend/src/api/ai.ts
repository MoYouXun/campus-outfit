import request from '@/utils/request'

export const aiTryOn = (data: { personImageUrl: string, outfitImageUrl: string }) => {
  return request.post('/ai/try-on', data)
}
