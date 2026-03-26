import request from '../utils/request'

export function likeOutfit(id: number | string, userId: number | string) {
  return request({
    url: `/like/${id}`,
    method: 'post',
    params: { userId }
  })
}

export function unlikeOutfit(id: number | string, userId: number | string) {
  return request({
    url: `/like/${id}`,
    method: 'delete',
    params: { userId }
  })
}

export function favoriteOutfit(id: number | string, userId: number | string) {
  return request({
    url: `/favorite/${id}`,
    method: 'post',
    params: { userId }
  })
}

export function unfavoriteOutfit(id: number | string, userId: number | string) {
  return request({
    url: `/favorite/${id}`,
    method: 'delete',
    params: { userId }
  })
}

export function addComment(data: any) {
  return request({
    url: '/comment',
    method: 'post',
    data
  })
}

export function getOutfitComments(id: number | string) {
  return request({
    url: `/comment/outfit/${id}`,
    method: 'get'
  })
}

export function deleteComment(id: number | string, userId: number | string) {
  return request({
    url: `/comment/${id}`,
    method: 'delete',
    params: { userId }
  })
}
