import axios from 'axios'
import type { ApiResponse } from '../types'
import { userMessage } from '../utils/uiSemantics'

export class ApiBusinessError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiBusinessError'
    this.code = code
  }
}

const FALLBACK_MESSAGES: Record<string, string> = {
  '0001': '系统暂时出现异常，请稍后重试。',
  '0002': '请求参数无效，请检查后重试。',
}

export function resolveFallbackMessage(code: string): string {
  return FALLBACK_MESSAGES[code] || FALLBACK_MESSAGES['0001']
}

function resolveMessage(value: unknown, code: string): string {
  return typeof value === 'string' && value.trim()
    ? userMessage(value)
    : resolveFallbackMessage(code)
}

const http = axios.create({
  baseURL: '/api',
  timeout: 360000,
})

http.interceptors.response.use(
  (res) => {
    const body: Partial<ApiResponse<unknown>> = res.data || {}
    if (body.code !== '0000') {
      const code = typeof body.code === 'string' && body.code ? body.code : '0001'
      return Promise.reject(new ApiBusinessError(
          code,
          resolveMessage(body.info, code),
      ))
    }
    return body.data as any
  },
  (err) => {
    const body: Partial<ApiResponse<unknown>> = err.response?.data || {}
    const code = typeof body.code === 'string' && body.code ? body.code : '0001'
    return Promise.reject(new ApiBusinessError(
        code,
        resolveMessage(body.info, code),
    ))
  },
)

export default http
