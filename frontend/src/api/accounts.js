import { api } from './client';

export const listAccounts = (config) => api.get('/admin/accounts', config);
export const linkEmployeeAccount = (accountId, employeeId) =>
  api.put(`/admin/accounts/${accountId}/employee`, { employeeId });
