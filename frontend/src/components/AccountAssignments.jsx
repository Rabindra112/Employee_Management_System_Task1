import { useEffect, useState } from 'react';
import { linkEmployeeAccount, listAccounts } from '../api/accounts';

export default function AccountAssignments({ employeeRevision }) {
  const [accounts, setAccounts] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    listAccounts({ signal: controller.signal })
      .then(response => {
        if (!controller.signal.aborted) {
          setAccounts(response.data.filter(account => ['EMPLOYEE', 'USER'].includes(account.role)));
          setSelectedId('');
          setEmployeeId('');
        }
      })
      .catch(err => {
        if (!controller.signal.aborted) {
          setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to load employee accounts.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [employeeRevision, revision]);

  function chooseAccount(value) {
    setSelectedId(value);
    const account = accounts.find(item => String(item.id) === value);
    setEmployeeId(account?.employeeId == null ? '' : String(account.employeeId));
    setNotice('');
    setError('');
  }

  async function saveAssignment(event) {
    event.preventDefault();
    if (saving || !selectedId) return;
    const id = employeeId.trim() === '' ? null : Number(employeeId);
    if (id !== null && (!Number.isSafeInteger(id) || id < 1)) {
      setError('Enter a valid employee ID from the employee list.');
      return;
    }
    setSaving(true);
    setError('');
    setNotice('');
    try {
      await linkEmployeeAccount(selectedId, id);
      setAccounts(current => current.map(account => String(account.id) === selectedId
        ? { ...account, employeeId: id }
        : account));
      setNotice(id === null ? 'Employee record unlinked. This account will show the pending assignment screen.' : `Account linked to employee #${id}.`);
    } catch (err) {
      setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to save the account assignment.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card account-assignments" aria-labelledby="assignments-heading">
      <div className="toolbar">
        <h2 id="assignments-heading">Employee account access</h2>
        <button type="button" className="secondary small" disabled={loading || saving}
          onClick={() => setRevision(current => current + 1)}>Refresh accounts</button>
      </div>
      <p>Link a registered employee account to its employee record so the employee can view their own profile and documents.</p>
      {error && <div className="error" role="alert">{error}</div>}
      {notice && <div className="auth-success" role="status">{notice}</div>}
      {loading ? <p className="section-status" role="status">Loading employee accounts...</p> : accounts.length === 0 ? (
        !error && <p className="section-status">No employee accounts yet. Employees can create an account from the sign-up screen.</p>
      ) : (
        <form className="assignment-form" onSubmit={saveAssignment}>
          <label htmlFor="assignment-account">Employee account
            <select id="assignment-account" value={selectedId} required disabled={saving}
              onChange={event => chooseAccount(event.target.value)}>
              <option value="">Select an account</option>
              {accounts.map(account => <option key={account.id} value={account.id}>
                {account.fullName} ({account.email}) — {account.employeeId == null ? 'Not linked' : `Employee #${account.employeeId}`}
              </option>)}
            </select>
          </label>
          <label htmlFor="assignment-employee">Employee ID
            <input id="assignment-employee" type="number" min="1" step="1" value={employeeId}
              disabled={saving || !selectedId} placeholder="ID from the employee list"
              aria-describedby="assignment-help" onChange={event => setEmployeeId(event.target.value)} />
          </label>
          <button type="submit" disabled={saving || !selectedId}>
            {saving ? 'Saving...' : 'Save assignment'}
          </button>
          <p id="assignment-help" className="field-help">Use the ID shown in the employee list above. Leave Employee ID empty to unlink an account. Each record can belong to one employee account.</p>
        </form>
      )}
    </section>
  );
}
