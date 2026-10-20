import { api } from './client';
export const listEmployees = (params, config = {}) => api.get('/employees', { ...config, params });
export const getStats = (config) => api.get('/employees/stats', config);
export const getMyEmployee = (config) => api.get('/employees/me', config);
export const addEmployee = (employee) => api.post('/employees', employee);
export const updateEmployee = (id, employee) => api.put(`/employees/${id}`, employee);
export const deleteEmployee = (id) => api.delete(`/employees/${id}`);
