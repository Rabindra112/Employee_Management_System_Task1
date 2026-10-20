import { useEffect, useState } from 'react';
import { listUsers, updateUserRole } from '../api/users';

export default function UserRoles({ currentUserId, onRoleChanged }) {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [selectedRole, setSelectedRole] = useState('USER');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    listUsers({ signal: controller.signal })
      .then(response => {
        if (!controller.signal.aborted) setUsers(response.data);
      })
      .catch(err => {
        if (!controller.signal.aborted) {
          setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to load registered users. Please try again.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [revision]);

  function startEditing(account) {
    setEditingId(account.id);
    setSelectedRole(account.role);
    setError('');
    setNotice('');
  }

  function refreshUsers() {
    setEditingId(null);
    setNotice('');
    setRevision(current => current + 1);
  }

  async function saveRole(event, account) {
    event.preventDefault();
    if (saving || selectedRole === account.role) return;
    setSaving(true);
    setError('');
    setNotice('');

    let updatedUser;
    try {
      const response = await updateUserRole(account.id, selectedRole);
      updatedUser = response.data;
      setUsers(current => current.map(item => item.id === updatedUser.id ? updatedUser : item));
      setEditingId(null);
      setNotice(`${updatedUser.fullName}'s role is now ${updatedUser.role}.`);
    } catch (err) {
      setError(err.response?.data?.detail || err.response?.data?.message ||
        (err.response?.status === 409
          ? 'Keep at least one administrator. Promote another user to ADMIN before changing the last administrator to USER.'
          : 'Unable to update this role. Please try again.'));
      setSaving(false);
      return;
    }

    try {
      await onRoleChanged?.(updatedUser);
    } catch {
      setError('The role was updated, but account access could not be refreshed. Refresh the page to load your current access.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card user-roles" aria-labelledby="user-roles-heading" aria-busy={loading || saving}>
      <div className="toolbar">
        <h2 id="user-roles-heading">User roles</h2>
        <button type="button" className="secondary small" disabled={loading || saving} onClick={refreshUsers}>
          Refresh users
        </button>
      </div>
      <p>Choose who can manage employees and account access. USER opens the employee dashboard; ADMIN opens the admin dashboard.</p>
      <p className="user-roles-help">Keep at least one administrator. Changes apply to signed-in users automatically.</p>
      {error && <div className="error" role="alert">{error}</div>}
      {notice && <div className="auth-success" role="status">{notice}</div>}
      {loading ? <p className="section-status" role="status">Loading registered users...</p> : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Current Role</th><th scope="col">Action</th></tr>
            </thead>
            <tbody>
              {users.map(account => (
                <tr key={account.id}>
                  <td><strong>{account.fullName}</strong>{account.id === currentUserId && <span className="user-roles-self">You</span>}</td>
                  <td>{account.email}</td>
                  <td><span className={`role-badge user-role-${account.role.toLowerCase()}`}>{account.role}</span></td>
                  <td>
                    {editingId === account.id ? (
                      <form className="user-role-form" onSubmit={event => saveRole(event, account)}>
                        <label htmlFor={`user-role-${account.id}`}>Select role for {account.fullName}
                          <select id={`user-role-${account.id}`} value={selectedRole} disabled={saving}
                            onChange={event => setSelectedRole(event.target.value)}>
                            <option value="USER">USER</option>
                            <option value="ADMIN">ADMIN</option>
                          </select>
                        </label>
                        <div className="actions">
                          <button type="submit" className="small" disabled={saving || selectedRole === account.role}>
                            {saving ? 'Updating...' : 'Update Role'}
                          </button>
                          <button type="button" className="secondary small" disabled={saving} onClick={() => setEditingId(null)}>
                            Cancel
                          </button>
                        </div>
                      </form>
                    ) : (
                      <button type="button" className="secondary small" disabled={saving} onClick={() => startEditing(account)}
                        aria-label={`Change role for ${account.fullName}`}>
                        Change Role
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {users.length === 0 && !error && <tr><td colSpan={4} className="empty">No registered users found.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
