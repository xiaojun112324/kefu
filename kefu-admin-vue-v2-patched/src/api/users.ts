import api from '@/api'

export function fetchUsers(params: { page?: number; size?: number; keyword?: string; role?: string; status?: number }) {
  return api.get('/users', { params })
}
export function getUser(id: number) {
  return api.get(`/users/${id}`)
}
export function createUser(payload: any) {
  return api.post('/users', payload)
}
export function updateUser(id: number, payload: any) {
  return api.put(`/users/${id}`, payload)
}
export function deleteUser(id: number) {
  return api.delete(`/users/${id}`)
}
export function batchDelete(ids: number[]) {
  return api.delete('/users', { params: { ids: ids.join(',') } })
}
export function setUserStatus(id: number, status: number) {
  return api.put(`/users/${id}/status`, { status })
}
export const getMe = () => api.get('/users/me')
export const updateMe = (payload:any) => api.put('/users/me', payload)