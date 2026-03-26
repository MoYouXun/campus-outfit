import request from '../utils/request'

export function getRecommendBySeason(params: any) {
  return request({
    url: '/recommend/season',
    method: 'get',
    params
  })
}

export function getRecommendByOccasion(params: any) {
  return request({
    url: '/recommend/occasion',
    method: 'get',
    params
  })
}

export function getRecommendByStyle(params: any) {
  return request({
    url: '/recommend/style',
    method: 'get',
    params
  })
}

export function getRecommendPersonalized(params: any) {
  return request({
    url: '/recommend/personal',
    method: 'get',
    params
  })
}
