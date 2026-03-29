import request from '../utils/request'

/**
 * 根据对象名获取图片的预签名URL
 * @param objectName MinIO中的对象名
 * @returns 有效的预签名URL
 */
export function getImageUrl(objectName: string) {
  return request({
    url: '/image/url',
    method: 'get',
    params: {
      objectName
    }
  })
}

/**
 * 上传试穿人像底图并进行 AI 预审
 * @param file 人像照片
 */
export async function uploadPortrait(file: File | Blob) {
  const formData = new FormData()
  formData.append('file', file)
  
  return request.post('/image/upload-portrait', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}