import axios from 'axios';
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/employees' });
export const listEmployees = (params) => api.get('', { params });
export const getStats = () => api.get('/stats');
export const addEmployee = (employee) => api.post('', employee);
export const updateEmployee = (id, employee) => api.put(`/${id}`, employee);
export const deleteEmployee = (id) => api.delete(`/${id}`);
