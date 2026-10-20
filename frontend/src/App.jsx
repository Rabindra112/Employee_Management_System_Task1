import { useCallback, useEffect, useRef, useState } from "react";

import {
  addEmployee,
  deleteEmployee,
  getStats,
  listEmployees,
  updateEmployee,
} from "./api/employees";

import EmployeeForm from "./components/EmployeeForm";
import AccountAssignments from "./components/AccountAssignments";
import UserRoles from "./components/UserRoles";
import DocumentApprovals from "./components/DocumentApprovals";

const PAGE_SIZE = 10;

const initialStats = {
  totalEmployees: 0,
  totalDepartments: 0,
  totalSalary: 0,
};

export default function Dashboard({ user, onLogout, onRoleChanged }) {
  // Employee data
  const [employees, setEmployees] = useState([]);
  const [stats, setStats] = useState(initialStats);

  // Search and pagination
  const [name, setName] = useState("");
  const [department, setDepartment] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Form state
  const [editingEmployee, setEditingEmployee] = useState(null);
  const [showForm, setShowForm] = useState(false);

  // Loading and error states
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [error, setError] = useState("");
  const [employeeRevision, setEmployeeRevision] = useState(0);
  const employeeRequest = useRef(null);

  // Format salary
  const formatSalary = (salary) => {
    return Number(salary).toLocaleString("en-IN", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  // Fetch employees and dashboard statistics
  const loadEmployees = useCallback(async () => {
    employeeRequest.current?.abort();
    const controller = new AbortController();
    employeeRequest.current = controller;
    setLoading(true);

    try {
      const [employeeResponse, statsResponse] = await Promise.all([
        listEmployees({
          name,
          department,
          page,
          size: PAGE_SIZE,
        }, { signal: controller.signal }),
        getStats({ signal: controller.signal }),
      ]);

      if (controller.signal.aborted) return;

      setEmployees(employeeResponse.data.content);
      setTotalPages(employeeResponse.data.totalPages);
      setStats(statsResponse.data);

      setError("");
    } catch (err) {
      if (controller.signal.aborted) return;
      setError(
        err.response?.data?.detail || err.response?.data?.message ||
          "Unable to fetch employees. Please check the backend connection."
      );
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [name, department, page]);

  // Load data when search, department or page changes
  useEffect(() => {
    loadEmployees();
    return () => employeeRequest.current?.abort();
  }, [loadEmployees]);

  // Open add employee form
  const handleAdd = () => {
    setEditingEmployee(null);
    setShowForm(true);
    setError("");
  };

  // Open edit employee form
  const handleEdit = (employee) => {
    setEditingEmployee(employee);
    setShowForm(true);
    setError("");
  };

  // Close employee form
  const handleCancel = () => {
    setShowForm(false);
    setEditingEmployee(null);
  };

  // Create or update employee
  const handleSave = async (employeeData) => {
    setSaving(true);
    setError("");

    try {
      if (editingEmployee) {
        await updateEmployee(editingEmployee.id, employeeData);
      } else {
        await addEmployee(employeeData);
      }

      setShowForm(false);
      setEditingEmployee(null);
      setEmployeeRevision(current => current + 1);

      // Return to first page after saving
      if (page !== 0) {
        setPage(0);
      } else {
        await loadEmployees();
      }
    } catch (err) {
      setError(
        err.response?.data?.detail || err.response?.data?.message ||
          "Unable to save employee. Please try again."
      );
    } finally {
      setSaving(false);
    }
  };

  // Delete employee
  const handleDelete = async (employee) => {
    const confirmed = window.confirm(
      `Are you sure you want to delete ${employee.name}?`
    );

    if (!confirmed) {
      return;
    }

    setDeletingId(employee.id);
    setError("");

    try {
      await deleteEmployee(employee.id);
      setEmployeeRevision(current => current + 1);

      // Move to previous page if last record is deleted
      if (page > 0 && employees.length === 1) {
        setPage((previousPage) => previousPage - 1);
      } else {
        await loadEmployees();
      }
    } catch (err) {
      setError(
        err.response?.data?.detail || err.response?.data?.message ||
          "Unable to delete employee. Please try again."
      );
    } finally {
      setDeletingId(null);
    }
  };

  // Handle name search
  const handleNameSearch = (event) => {
    setName(event.target.value);
    setPage(0);
  };

  // Handle department filtering
  const handleDepartmentFilter = (event) => {
    setDepartment(event.target.value);
    setPage(0);
  };

  return (
    <main className="layout admin-dashboard">

      {/* ================= HEADER ================= */}
      <header>
        <div>
          <span className="eyebrow">EMPLOYEE MANAGEMENT SYSTEM</span>

          <h1>Admin Dashboard</h1>

          <p>Manage employee records, payroll totals and account access.</p>
        </div>

        <div className="header-actions"><span>Welcome, {user?.fullName}</span><span className="role-badge">Admin</span><button type="button" className="secondary" onClick={onLogout}>Logout</button><button type="button" onClick={handleAdd}>
          + Add Employee
        </button></div>
      </header>

      {/* ================= DASHBOARD ================= */}
      <section className="stats">

        <article className="card">
          <span>Total Employees</span>
          <strong>{stats.totalEmployees}</strong>
        </article>

        <article className="card">
          <span>Total Departments</span>
          <strong>{stats.totalDepartments}</strong>
        </article>

        <article className="card">
          <span>Total Monthly Salary</span>
          <strong>
            ₹{formatSalary(stats.totalSalary)}
          </strong>
        </article>

      </section>

      {/* ================= ERROR MESSAGE ================= */}
      {error && (
        <div className="error" role="alert">
          <span>{error}</span>

          <button
            className="secondary"
            onClick={() => setError("")}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* ================= EMPLOYEE FORM ================= */}
      {showForm && (
        <EmployeeForm
          employee={editingEmployee}
          onSave={handleSave}
          onCancel={handleCancel}
          saving={saving}
        />
      )}

      {/* ================= EMPLOYEE TABLE ================= */}
      <section className="card table-card">

        {/* Search and filter */}
        <div className="toolbar">

          <h2>Employee List</h2>

          <div className="filters">

            <input
              type="text"
              placeholder="Search employee name"
              aria-label="Search employee name"
              value={name}
              onChange={handleNameSearch}
            />

            <input
              type="text"
              placeholder="Filter by department"
              aria-label="Filter by department"
              value={department}
              onChange={handleDepartmentFilter}
            />

          </div>
        </div>

        {/* Employee table */}
        <div className="table-wrap">

          <table>

            <thead>
              <tr>
                <th>Employee ID</th>
                <th>Employee Name</th>
                <th>Department</th>
                <th>Salary</th>
                <th>Actions</th>
              </tr>
            </thead>

            <tbody>

              {employees.map((employee) => (
                <tr key={employee.id}>

                  <td>#{employee.id}</td>

                  <td>
                    <strong>{employee.name}</strong>
                  </td>

                  <td>{employee.department}</td>

                  <td>
                    ₹{formatSalary(employee.salary)}
                  </td>

                  <td>

                    <button
                      className="secondary small"
                      onClick={() => handleEdit(employee)}
                    >
                      Edit
                    </button>

                    {" "}

                    <button
                      className="danger small"
                      disabled={deletingId !== null}
                      onClick={() => handleDelete(employee)}
                    >
                      {deletingId === employee.id
                        ? "Deleting..."
                        : "Delete"}
                    </button>

                  </td>
                </tr>
              ))}

              {/* Empty employee list */}
              {!loading && employees.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty">
                    No employees found.
                  </td>
                </tr>
              )}

            </tbody>
          </table>

        </div>

        {/* ================= PAGINATION ================= */}
        <footer>

          <span>
            {loading
              ? "Loading employees..."
              : `Page ${
                  totalPages === 0 ? 0 : page + 1
                } of ${totalPages}`}
          </span>

          <div className="actions">

            <button
              className="secondary"
              disabled={page === 0 || loading}
              onClick={() =>
                setPage((previousPage) => previousPage - 1)
              }
            >
              Previous
            </button>

            <button
              className="secondary"
              disabled={
                page + 1 >= totalPages || loading
              }
              onClick={() =>
                setPage((previousPage) => previousPage + 1)
              }
            >
              Next
            </button>

          </div>

        </footer>

      </section>
      <UserRoles currentUserId={user?.id} onRoleChanged={async updatedUser => {
        setEmployeeRevision(current => current + 1);
        await onRoleChanged?.(updatedUser);
      }} />
      <AccountAssignments employeeRevision={employeeRevision} />
      <DocumentApprovals />
    </main>
  );
}
