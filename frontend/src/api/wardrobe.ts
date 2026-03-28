import request from '@/utils/request'

/**
 * 获取用户衣柜列表
 */
export async function getWardrobeList() {
  return request.get('/wardrobe/list')
}

/**
 * 根据类型筛选衣柜单品
 */
export async function getWardrobeListByType(type: string) {
  return request.get('/wardrobe/listByType', { params: { type } })
}

/**
 * 根据季节筛选衣柜单品
 */
export async function getWardrobeListBySeason(season: string) {
  return request.get('/wardrobe/listBySeason', { params: { season } })
}

/**
 * 根据风格筛选衣柜单品
 */
export async function getWardrobeListByStyle(style: string) {
  return request.get('/wardrobe/listByStyle', { params: { style } })
}

/**
 * 获取单品详情
 */
export async function getWardrobeDetail(id: number | string) {
  return request.get(`/wardrobe/detail/${id}`)
}

/**
 * 删除衣柜单品
 */
export async function deleteWardrobeItem(id: number | string) {
  return request.delete(`/wardrobe/delete/${id}`)
}

/**
 * 上传单品到电子衣橱
 * @param file 单品图片文件
 * @param metadata 其他属性
 */
export async function uploadWardrobeItem(file: File, metadata: { type: string, color: string, style: string, season: string }) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', metadata.type)
  formData.append('color', metadata.color)
  formData.append('style', metadata.style)
  formData.append('season', metadata.season)
  
  return request.post('/wardrobe/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
