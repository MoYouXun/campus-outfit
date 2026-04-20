import request from '../utils/request'

export function getHotRanking(params: any) {
  return request({
    url: '/ranking/hot',
    method: 'get',
    params
  })
}

export function refreshRankingCache() {
  return request({
    url: '/ranking/refresh',
    method: 'post'
  })
}
