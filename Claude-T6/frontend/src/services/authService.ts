import http from './http'

export default {
  getToken(userId: number, tenantId: number) {
    return http.post('/auth/token', { userId, tenantId }).then(r => r.data)
  }
}
