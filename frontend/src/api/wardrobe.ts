import request from '@/utils/request'

/**
 * 获取衣柜所有单品列表
 */
export async function getWardrobeList() {
  return request.get('/wardrobe/list')
}

/**
 * 删除电子衣橱单品
 * @param id 单品ID
 */
export async function deleteWardrobeItem(id: number | string) {
  return request.delete(`/wardrobe/${id}`)
}

/**
 * 上传单品到电子衣橱
 * @param file 单品图片文件
 */
export async function uploadWardrobeItem(file: File | Blob) {
  const formData = new FormData()
  formData.append('file', file)
  
  return request.post('/wardrobe/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
