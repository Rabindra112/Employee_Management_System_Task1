import { api } from './client';

export const listEmployeeDocuments = (employeeId, config) =>
  api.get(`/documents/employee/${employeeId}`, config);

export const listDocumentVersions = (documentId, config) =>
  api.get(`/documents/${documentId}/versions`, config);

export const downloadDocumentVersion = (documentId, versionNumber) =>
  api.get(`/documents/${documentId}/versions/${versionNumber}/download`, {
    responseType: 'blob',
  });

export const listMyDocuments = (config) => api.get('/documents/my', config);
export const listPendingDocuments = (config) => api.get('/documents/pending', config);
export const listDocumentTypes = (config) => api.get('/documents/types', config);
export const uploadMyDocument = (documentTypeId, file) => {
  const form = new FormData();
  form.append('documentTypeId', documentTypeId);
  form.append('file', file);
  return api.post('/documents/my/upload', form);
};
export const resubmitDocument = (documentId, file) => {
  const form = new FormData();
  form.append('file', file);
  return api.post(`/documents/${documentId}/resubmit`, form);
};
export const approveDocumentVersion = (documentId, version) =>
  api.patch(`/documents/${documentId}/versions/${version}/approve`);
export const rejectDocumentVersion = (documentId, version, reason) =>
  api.patch(`/documents/${documentId}/versions/${version}/reject`, { reason });
