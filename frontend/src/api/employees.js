import { api } from './client';
export const listEmployees = (params) => api.get('/employees', { params });
export const getStats = () => api.get('/employees/stats');
export const addEmployee = (employee) => api.post('/employees', employee);
export const updateEmployee = (id, employee) => api.put(`/employees/${id}`, employee);
export const deleteEmployee = (id) => api.delete(`/employees/${id}`);
