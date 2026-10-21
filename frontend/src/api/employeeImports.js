import { api } from "./client";

export const uploadEmployeeCsv = file => {
  const data = new FormData();
  data.append("file", file);
  return api.post("/admin/employee-import/uploads", data);
};

export const startEmployeeImport = uploadId =>
  api.post("/admin/employee-import/jobs", { uploadId });

export const getEmployeeImport = executionId =>
  api.get(`/admin/employee-import/jobs/${executionId}`);

export const getEmployeeImportHistory = () =>
  api.get("/admin/employee-import/jobs");

export const downloadEmployeeImportErrors = executionId =>
  api.get(`/admin/employee-import/jobs/${executionId}/errors`, { responseType: "blob" });

export const downloadEmployeeTemplate = () =>
  api.get("/admin/employees/template", { responseType: "blob" });

export const downloadEmployees = format =>
  api.get(`/admin/employees/export.${format}`, { responseType: "blob" });
