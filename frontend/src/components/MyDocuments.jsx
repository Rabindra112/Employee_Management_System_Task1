import { useEffect, useState } from 'react';
import { downloadDocumentVersion, listDocumentTypes, listMyDocuments, listDocumentVersions, resubmitDocument, uploadMyDocument } from '../api/documents';

function saveBlob(response, name) {
  const url = URL.createObjectURL(response.data);
  const link = window.document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

export default function MyDocuments() {
  const [documents, setDocuments] = useState([]);
  const [types, setTypes] = useState([]);
  const [typeId, setTypeId] = useState('');
  const [file, setFile] = useState(null);
  const [history, setHistory] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    Promise.allSettled([listMyDocuments(), listDocumentTypes()]).then(results => {
      const [docs, docTypes] = results;
      const errors = [];
      if (docs.status === 'fulfilled') setDocuments(docs.value.data);
      else errors.push(`documents: ${docs.reason.response?.data?.detail || docs.reason.message || 'request failed'}`);
      if (docTypes.status === 'fulfilled') {
        setTypes(docTypes.value.data);
        setTypeId(current => current || (docTypes.value.data[0] ? String(docTypes.value.data[0].id) : ''));
      } else errors.push(`document types: ${docTypes.reason.response?.data?.detail || docTypes.reason.message || 'request failed'}`);
      setError(errors.length ? `Unable to load ${errors.join('; ')}` : '');
    });
  }, [revision]);

  async function upload(documentId = null) {
    if (!file || busy || (!documentId && !typeId)) return;
    setBusy(true); setError('');
    try {
      if (documentId) await resubmitDocument(documentId, file);
      else await uploadMyDocument(typeId, file);
      setFile(null);
      setRevision(value => value + 1);
    } catch (err) { setError(err.response?.data?.detail || 'Unable to upload this document.'); }
    finally { setBusy(false); }
  }

  async function showHistory(id) {
    try { const response = await listDocumentVersions(id); setHistory(current => ({ ...current, [id]: response.data })); }
    catch (err) { setError(err.response?.data?.detail || 'Unable to load version history.'); }
  }

  return <section className="card employee-documents" aria-labelledby="my-documents-heading">
    <div className="toolbar"><h2 id="my-documents-heading">My Documents</h2><button type="button" className="secondary small" onClick={() => setRevision(v => v + 1)}>Refresh</button></div>
    <p>Uploads are reviewed by an administrator. Only approved versions can be downloaded.</p>
    <div className="assignment-form">
      <label>Document type<select value={typeId} onChange={event => setTypeId(event.target.value)}><option value="">Select type</option>{types.map(type => <option key={type.id} value={type.id}>{type.description ? `${type.description} (${type.name})` : type.name}</option>)}</select></label>
      <label>File<input type="file" accept=".pdf,.docx,.jpg,.jpeg,.png" onChange={event => setFile(event.target.files?.[0] || null)} /></label>
      <button type="button" disabled={busy || !file || !typeId} onClick={() => upload()}>{busy ? 'Uploading...' : 'Upload'}</button>
    </div>
    {error && <div className="error" role="alert">{error}</div>}
    {documents.length === 0 ? <p className="section-status">No documents uploaded yet.</p> : <ul className="document-list">{documents.map(doc => <li className="document-item" key={doc.id}>
      <div className="document-summary"><div><strong>{doc.documentName}</strong><p>{doc.documentType} · Version {doc.currentVersion} · {doc.status || 'PENDING'}</p>{doc.rejectionReason && <p>Reason: {doc.rejectionReason}</p>}</div>
        <div className="actions"><button className="secondary small" type="button" onClick={() => showHistory(doc.id)}>Version History</button>
          {doc.status === 'REJECTED' && <button className="secondary small" type="button" disabled={!file || busy} onClick={() => upload(doc.id)}>Resubmit</button>}
          {doc.latestApprovedVersion != null && <button className="secondary small" type="button" onClick={async () => { try { const v = await downloadDocumentVersion(doc.id, doc.latestApprovedVersion); saveBlob(v, doc.documentName); } catch { setError('Unable to download document.'); } }}>Download latest approved</button>}
      </div></div>
      {history[doc.id] && <ul className="version-list">{history[doc.id].map(v => <li key={v.id}><div><strong>Version {v.versionNumber} · {v.status}</strong><span>{v.fileName}{v.rejectionReason ? ` · Rejected: ${v.rejectionReason}` : ''}</span></div>{v.status === 'APPROVED' && <button className="secondary small" type="button" onClick={async () => { try { saveBlob(await downloadDocumentVersion(doc.id, v.versionNumber), v.fileName); } catch { setError('Unable to download document.'); } }}>Download</button>}</li>)}</ul>}
    </li>)}</ul>}
  </section>;
}
