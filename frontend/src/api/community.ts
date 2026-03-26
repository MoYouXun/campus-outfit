import request from '../utils/request'

export function getCommunityFeed(params: any) {
  return request({
    url: '/community/feed',
    method: 'get',
    params
  })
}

export function getFollowingFeed(params: { currentUserId: number | string, page: number, size: number }) {
  return request({
    url: '/community/following',
    method: 'get',
    params
  })
}

export function getCommunityOutfitDetail(id: number | string, currentUserId?: number | null) {
  return request({
    url: `/community/outfit/${id}`,
    method: 'get',
    params: { currentUserId }
  })
}

export function getHotTopics() {
  return request({
    url: '/community/topics',
    method: 'get'
  })
}

export function getTopicOutfits(id: number | string, params: any) {
  return request({
    url: `/topic/${id}/outfits`,
    method: 'get',
    params
  })
}
