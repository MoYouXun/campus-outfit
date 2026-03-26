import request from '../utils/request'

export function uploadAndAnalyze(files: File[]) {
  console.log('uploadAndAnalyze调用，files:', files)
  const formData = new FormData()
  files.forEach(file => {
    console.log('添加文件:', file.name, file.size, file.type)
    formData.append('files', file)
  })
  
  // 检查FormData中的内容
  for (let [key, value] of formData.entries()) {
    console.log(`FormData ${key}:`, value)
  }
  
  return request({
    url: '/outfit/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  }).catch(error => {
    console.error('uploadAndAnalyze请求失败:', error)
    console.error('错误配置:', error.config)
    if (error.response) {
      console.error('错误响应:', error.response)
      console.error('响应状态:', error.response.status)
      console.error('响应数据:', error.response.data)
    } else if (error.request) {
      console.error('没有收到响应:', error.request)
    } else {
      console.error('请求配置错误:', error.message)
    }
    throw error
  })
}

export function publishOutfit(data: any) {
  return request({
    url: '/outfit/publish',
    method: 'post',
    data
  })
}

export function getOutfitDetail(id: number | string) {
  return request({
    url: `/outfit/${id}`,
    method: 'get'
  })
}

export function getMyOutfits(params: any) {
  return request({
    url: '/outfit/mine',
    method: 'get',
    params
  })
}

export function deleteOutfit(id: number | string) {
  return request({
    url: `/outfit/${id}`,
    method: 'delete'
  })
}

export function incrementViewCount(id: number | string) {
  return request({
    url: `/outfit/${id}/view`,
    method: 'post'
  })
}
