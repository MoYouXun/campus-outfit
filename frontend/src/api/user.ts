import request from '../utils/request'

export function login(data: any) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

export function register(data: any) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

export function getUserInfo(id: number | string, currentUserId?: number | string) {
  return request({
    url: `/user/${id}`,
    method: 'get',
    params: { currentUserId }
  })
}

export function updateProfile(data: any) {
  return request({
    url: '/user/profile',
    method: 'put',
    data
  })
}

export function followUser(id: number | string) {
  return request({
    url: `/user/follow/${id}`,
    method: 'post'
  })
}

export function unfollowUser(id: number | string) {
  return request({
    url: `/user/follow/${id}`,
    method: 'delete'
  })
}

export function getFollows(userId: number | string, params: any) {
  return request({
    url: `/follow/${userId}/followings`,
    method: 'get',
    params
  })
}

export function getFans(userId: number | string, params: any) {
  return request({
    url: `/follow/${userId}/followers`,
    method: 'get',
    params
  })
}
