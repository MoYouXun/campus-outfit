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