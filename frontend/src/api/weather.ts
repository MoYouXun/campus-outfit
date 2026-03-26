import request from '../utils/request'

export function getWeatherNow(params: { city?: string; latitude?: number; longitude?: number }) {
  return request({
    url: '/weather/now',
    method: 'get',
    params
  })
}
