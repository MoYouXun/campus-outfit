import request from '../utils/request'

// \u63a8\u8350\u63a5\u53e3\u516c\u5171\u5206\u9875\u53c2\u6570
export interface RecommendPageParams {
  page?: number
  size?: number
}

// \u5929\u6c14/\u5730\u7406\u4f4d\u7f6e\u53c2\u6570
export interface LocationParams {
  city?: string
  latitude?: number
  longitude?: number
}

export interface SeasonParams extends RecommendPageParams, LocationParams {}

export interface OccasionParams extends RecommendPageParams {
  occasion: string
}

export interface StyleParams extends RecommendPageParams {}



export function getRecommendBySeason(params: SeasonParams) {
  return request({
    url: '/recommend/season',
    method: 'get',
    params
  })
}

export function getRecommendByOccasion(params: OccasionParams) {
  return request({
    url: '/recommend/occasion',
    method: 'get',
    params
  })
}

export function getRecommendByStyle(params: StyleParams) {
  return request({
    url: '/recommend/style',
    method: 'get',
    params
  })
}

/**
 * AI 穿搭分析
 * @param data { base64Image: string, sessionId: string }
 */
export function aiAnalyze(data: { base64Image: string, sessionId: string }) {
  return request({
    url: '/ai/assistant/analyze',
    method: 'post',
    data
  })
}

/**
 * AI 穿搭智能聊天 (支持视觉多模态)
 * @param data { message: string, sessionId: string, imageUrls?: string[] }
 */
export function aiChat(data: { message: string, sessionId: string, imageUrls?: string[] }) {
  return request({
    url: '/ai/assistant/chat',
    method: 'post',
    data
  })
}


