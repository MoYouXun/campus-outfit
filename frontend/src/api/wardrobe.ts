import request from '@/utils/request'

/**
 * 上传单品到电子衣橱
 * @param file 单品图片文件
 */
export async function uploadWardrobeItem(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  
  return request.post('/wardrobe/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
