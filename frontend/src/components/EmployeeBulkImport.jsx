import { useEffect, useState } from "react";
import {
  downloadEmployeeImportErrors,
  downloadEmployeeTemplate,
  downloadEmployees,
  getEmployeeImport,
  getEmployeeImportHistory,
  startEmployeeImport,
  uploadEmployeeCsv,
} from "../api/employeeImports";

export default function EmployeeBulkImport({ onImported }) {
  const [file, setFile] = useState(null);
  const [job, setJob] = useState(null);
  const [history, setHistory] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const download = async (request, filename) => {
    try {
      const response = await request;
      const url = URL.createObjectURL(response.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      setError("Unable to download the requested employee file.");
    }
  };

  const refreshHistory = async () => {
    try {
      const response = await getEmployeeImportHistory();
      setHistory(response.data);
    } catch {
      setError("Unable to load employee import history.");
    }
  };

  useEffect(() => { refreshHistory(); }, []);

  useEffect(() => {
    if (!job || !["STARTING", "STARTED"].includes(job.status)) return undefined;
    const timer = window.setInterval(async () => {
      try {
        const response = await getEmployeeImport(job.executionId);
        setJob(response.data);
        if (!["STARTING", "STARTED"].includes(response.data.status)) {
          refreshHistory();
          if (response.data.importedCount > 0) onImported?.();
        }
      } catch {
        setError("Unable to refresh the import status.");
      }
    }, 1500);
    return () => window.clearInterval(timer);
  }, [job?.executionId, job?.status]);

  const submit = async event => {
    event.preventDefault();
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      const uploaded = await uploadEmployeeCsv(file);
      const started = await startEmployeeImport(uploaded.data.uploadId);
      setJob(started.data);
      if (started.data.status === "COMPLETED" && started.data.importedCount > 0) onImported?.();
      await refreshHistory();
    } catch (err) {
      setError(err.response?.data?.message || "Unable to start employee import.");
    } finally {
      setBusy(false);
    }
  };

  const downloadErrors = async executionId => {
    try {
      const response = await downloadEmployeeImportErrors(executionId);
      const url = URL.createObjectURL(response.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = `employee-import-errors-${executionId}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("Unable to download the error report.");
    }
  };

  const rows = job ? [job, ...history.filter(item => item.executionId !== job.executionId)] : history;

  return (
    <section className="card employee-import" id="employee-bulk-import">
      <div className="toolbar">
        <div>
          <h2>Employee Bulk Import</h2>
          <p>Upload a CSV with the columns: name, department, salary.</p>
        </div>
        <div className="actions">
          <button className="secondary" type="button" onClick={() => download(downloadEmployeeTemplate(), "employee-import-template.csv")}>Download Template</button>
          <button className="secondary" type="button" onClick={() => download(downloadEmployees("csv"), "employees.csv")}>Download CSV</button>
          <button className="secondary" type="button" onClick={() => download(downloadEmployees("xlsx"), "employees.xlsx")}>Download Excel</button>
          <button className="secondary" type="button" onClick={refreshHistory}>Refresh</button>
        </div>
      </div>
      <form className="employee-import-form" onSubmit={submit}>
        <label htmlFor="employee-import-file">Employee CSV</label>
        <input id="employee-import-file" type="file" accept=".csv,text/csv" onChange={event => setFile(event.target.files?.[0] || null)} />
        <button type="submit" disabled={!file || busy}>{busy ? "Starting…" : "Upload and import"}</button>
      </form>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>File</th><th>Status</th><th>Imported</th><th>Errors</th><th>Report</th></tr></thead>
          <tbody>
            {rows.map(item => <tr key={item.executionId}>
              <td>{item.filename}</td><td>{item.status}{item.message && <small className="employee-import-message">{item.message}</small>}</td><td>{item.importedCount}</td><td>{item.errorCount}</td>
              <td>{item.errorCount > 0 && <button type="button" className="secondary small" onClick={() => downloadErrors(item.executionId)}>Download CSV</button>}</td>
            </tr>)}
            {rows.length === 0 && <tr><td colSpan={5} className="empty">No employee imports yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}
