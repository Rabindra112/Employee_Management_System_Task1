import { useEffect, useState } from 'react';
import { approveDocumentVersion, downloadDocumentVersion, listDocumentVersions, listPendingDocuments, rejectDocumentVersion } from '../api/documents';

export default function DocumentApprovals() {
  const [pending, setPending] = useState([]);
  const [history, setHistory] = useState({});
  const [reasons, setReasons] = useState({});
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    listPendingDocuments().then(response => setPending(response.data))
      .catch(err => setError(err.response?.data?.detail || 'Unable to load pending documents.'));
  }, [revision]);

  async function review(doc, approve) {
    try {
      if (approve) await approveDocumentVersion(doc.documentId, doc.versionNumber);
      else {
        const reason = reasons[`${doc.documentId}:${doc.versionNumber}`]?.trim();
        if (!reason) { setError('Enter a rejection reason first.'); return; }
        await rejectDocumentVersion(doc.documentId, doc.versionNumber, reason);
      }
      setError(''); setRevision(value => value + 1);
    } catch (err) { setError(err.response?.data?.detail || 'Unable to review this document.'); }
  }

  async function showHistory(id) {
    try { const result = await listDocumentVersions(id); setHistory(current => ({ ...current, [id]: result.data })); }
    catch (err) { setError(err.response?.data?.detail || 'Unable to load version history.'); }
  }

  return <section className="card employee-documents" aria-labelledby="document-approvals-heading">
    <div className="toolbar"><h2 id="document-approvals-heading">Document Approvals</h2><button className="secondary small" type="button" onClick={() => setRevision(value => value + 1)}>Refresh</button></div>
    {error && <div className="error" role="alert">{error}</div>}
    {pending.length === 0 ? <p className="section-status">No pending documents.</p> : <div className="table-wrap"><table>
      <thead><tr><th>User</th><th>Document</th><th>Version</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>{pending.map(doc => <tr key={`${doc.documentId}:${doc.versionNumber}`}>
        <td>{doc.uploadedBy}</td><td>{doc.fileName}<br /><small>{doc.documentType}</small></td><td>{doc.versionNumber}</td><td>{doc.status}</td>
        <td><div className="actions"><button className="secondary small" type="button" onClick={async () => { try { const result = await downloadDocumentVersion(doc.documentId, doc.versionNumber); const url = URL.createObjectURL(result.data); const a = window.document.createElement('a'); a.href = url; a.download = doc.fileName; a.click(); URL.revokeObjectURL(url); } catch (err) { setError(err.response?.data?.detail || 'Unable to download document.'); } }}>View / Download</button>
          <button className="secondary small" type="button" onClick={() => showHistory(doc.documentId)}>Version History</button>
          <button className="small" type="button" onClick={() => review(doc, true)}>Approve</button>
          <input aria-label={`Rejection reason for ${doc.fileName}`} value={reasons[`${doc.documentId}:${doc.versionNumber}`] || ''} onChange={event => setReasons(current => ({ ...current, [`${doc.documentId}:${doc.versionNumber}`]: event.target.value }))} placeholder="Rejection reason" />
          <button className="danger small" type="button" onClick={() => review(doc, false)}>Reject</button>
        </div></td>
      </tr>)}</tbody>
    </table></div>}
    {Object.entries(history).map(([id, versions]) => <ul className="version-list" key={id}>{versions.map(version => <li key={version.id}><div><strong>Document {id} · Version {version.versionNumber} · {version.status}</strong><span>{version.fileName}{version.rejectionReason ? ` · ${version.rejectionReason}` : ''}</span></div></li>)}</ul>)}
  </section>;
}
