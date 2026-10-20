import { useEffect, useState } from 'react';
import { getMyEmployee } from './api/employees';
import { downloadDocumentVersion, listDocumentVersions, listEmployeeDocuments } from './api/documents';
import MyDocuments from './components/MyDocuments';

const formatSalary = salary => Number(salary).toLocaleString('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: 2,
});

function formatDate(value) {
  if (!value) return 'Date unavailable';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleDateString('en-IN');
}

function DocumentVersions({ document }) {
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [downloading, setDownloading] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    listDocumentVersions(document.id, { signal: controller.signal })
      .then(response => {
        if (!controller.signal.aborted) {
          setVersions([...response.data].sort((a, b) => b.versionNumber - a.versionNumber));
        }
      })
      .catch(err => {
        if (!controller.signal.aborted) setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to load document versions.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [document.id, revision]);

  async function download(version) {
    if (downloading !== null) return;
    setDownloading(version.versionNumber);
    setError('');
    try {
      const response = await downloadDocumentVersion(document.id, version.versionNumber);
      const url = URL.createObjectURL(response.data);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = version.fileName || `document-${document.id}-v${version.versionNumber}`;
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      setError('Unable to download this document. Please try again or contact your administrator.');
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="document-versions">
      {error && <div className="error" role="alert"><span>{error}</span>
        <button className="secondary small" type="button" onClick={() => setRevision(current => current + 1)}>Retry</button>
      </div>}
      {loading ? <p role="status">Loading versions...</p> : versions.length === 0 ? (
        !error && <p>No versions available. Please contact your administrator.</p>
      ) : (
        <ul className="version-list">
          {versions.map(version => <li key={version.id}>
            <div>
              <strong>Version {version.versionNumber}{version.versionNumber === document.currentVersion ? ' · Current' : ''}</strong>
              <span>{version.fileName} · {formatDate(version.uploadedAt)}</span>
            </div>
            <button type="button" className="secondary small" disabled={downloading !== null}
              onClick={() => download(version)} aria-label={`Download ${version.fileName || document.documentName}, version ${version.versionNumber}`}>
              {downloading === version.versionNumber ? 'Downloading...' : 'Download'}
            </button>
          </li>)}
        </ul>
      )}
    </div>
  );
}

function EmployeeDocuments({ employeeId }) {
  const [documents, setDocuments] = useState([]);
  const [expandedId, setExpandedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    listEmployeeDocuments(employeeId, { signal: controller.signal })
      .then(response => {
        if (!controller.signal.aborted) setDocuments(response.data);
      })
      .catch(err => {
        if (!controller.signal.aborted) setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to load your documents.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [employeeId, revision]);

  return (
    <section className="card employee-documents" aria-labelledby="documents-heading">
      <div className="toolbar">
        <h2 id="documents-heading">My documents</h2>
        <button type="button" className="secondary small" disabled={loading}
          onClick={() => setRevision(current => current + 1)}>Refresh documents</button>
      </div>
      <p>View and download documents provided by your administrator.</p>
      {error && <div className="error" role="alert">{error}</div>}
      {loading ? <p className="section-status" role="status">Loading your documents...</p> : !error && documents.filter(document => document.ownerUserId == null).length === 0 ? (
        <p className="section-status">No documents have been added to your employee record yet.</p>
      ) : (
        <ul className="document-list">
          {documents.filter(document => document.ownerUserId == null).map(document => <li key={document.id} className="document-item">
            <div className="document-summary">
              <div>
                <strong>{document.documentName || document.documentType || 'Employee document'}</strong>
                <p>{document.documentType} · Added {formatDate(document.createdAt)}</p>
              </div>
              <button type="button" className="secondary small" aria-expanded={expandedId === document.id}
                aria-controls={`document-versions-${document.id}`}
                onClick={() => setExpandedId(current => current === document.id ? null : document.id)}>
                {expandedId === document.id ? 'Hide versions' : 'View versions'}
              </button>
            </div>
            {expandedId === document.id && <div id={`document-versions-${document.id}`}>
              <DocumentVersions document={document} />
            </div>}
          </li>)}
        </ul>
      )}
    </section>
  );
}

export default function EmployeeDashboard({ user, onLogout }) {
  const [employee, setEmployee] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setPending(false);
    setEmployee(null);
    getMyEmployee({ signal: controller.signal })
      .then(response => {
        if (!controller.signal.aborted) setEmployee(response.data);
      })
      .catch(err => {
        if (controller.signal.aborted) return;
        if (err.response?.status === 404) {
          setPending(true);
        } else {
          setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to load your employee profile. Please try again.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [revision]);

  return (
    <main className="layout employee-dashboard">
      <header>
        <div>
          <span className="eyebrow">EMPLOYEE MANAGEMENT SYSTEM</span>
          <h1>Employee Dashboard</h1>
          <p>Welcome, {user.fullName}. Your profile and documents are here.</p>
        </div>
        <div className="header-actions">
          <span className="role-badge">Employee</span>
          <button type="button" className="secondary" onClick={onLogout}>Logout</button>
        </div>
      </header>

      <section className="card profile-card" aria-labelledby="account-heading">
        <div className="toolbar">
          <h2 id="account-heading">My account</h2>
          <button type="button" className="secondary small" disabled={loading}
            onClick={() => setRevision(current => current + 1)}>Refresh profile</button>
        </div>
        <dl className="profile-details">
          <div><dt>Full name</dt><dd>{user.fullName}</dd></div>
          <div><dt>Email address</dt><dd>{user.email}</dd></div>
          <div><dt>Access</dt><dd>Employee</dd></div>
        </dl>
      </section>

      {loading && <p className="section-status" role="status">Loading your employee profile...</p>}
      {error && <div className="error" role="alert">{error}</div>}
      <MyDocuments />
      {pending && <section className="card pending-assignment" aria-labelledby="pending-heading">
        <span className="role-badge">Pending assignment</span>
        <h2 id="pending-heading">Your account is ready</h2>
        <p>An administrator needs to link your account to your employee record. Contact your administrator with your email address, {user.email}. Your employee details will appear here after it is linked.</p>
        <button type="button" className="secondary" onClick={() => setRevision(current => current + 1)}>Check assignment</button>
      </section>}
      {employee && <>
        <section className="stats" aria-label="My employee details">
          <article className="card"><span>Employee ID</span><strong>#{employee.id}</strong></article>
          <article className="card"><span>Department</span><strong>{employee.department}</strong></article>
          <article className="card"><span>Monthly salary</span><strong>{formatSalary(employee.salary)}</strong></article>
        </section>
        <section className="card profile-card" aria-labelledby="employment-heading">
          <h2 id="employment-heading">My employee record</h2>
          <dl className="profile-details">
            <div><dt>Employee name</dt><dd>{employee.name}</dd></div>
            <div><dt>Record status</dt><dd>Linked to your account</dd></div>
          </dl>
          <p>Contact your administrator if your employee details need to be updated.</p>
        </section>
        <EmployeeDocuments key={employee.id} employeeId={employee.id} />
      </>}
    </main>
  );
}
