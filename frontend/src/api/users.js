import { api } from './client';

export const listUsers = (config) => api.get('/admin/users', config);

export const updateUserRole = (id, role) => api.patch(`/admin/users/${id}/role`, { role });
