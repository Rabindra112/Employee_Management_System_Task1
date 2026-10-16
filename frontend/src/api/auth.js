import { api } from './client';
export const login = data => api.post('/auth/login',data);
export const signup = data => api.post('/auth/signup',data);
export const me = () => api.get('/auth/me');
