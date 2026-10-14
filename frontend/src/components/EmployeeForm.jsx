import { useEffect, useState } from "react";

const emptyForm = {
  name: "",
  department: "",
  salary: "",
};

export default function EmployeeForm({
  employee,
  onSave,
  onCancel,
  saving,
}) {
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");

  // Load employee details when editing
  useEffect(() => {
    if (employee) {
      setForm({
        name: employee.name,
        department: employee.department,
        salary: String(employee.salary),
      });
    } else {
      setForm(emptyForm);
    }

    setError("");
  }, [employee]);

  // Handle input changes
  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));

    setError("");
  };

  // Handle form submission
  const handleSubmit = (event) => {
    event.preventDefault();

    const name = form.name.trim();
    const department = form.department.trim();
    const salary = Number(form.salary);

    // Validate employee name
    if (!name) {
      setError("Employee name is required.");
      return;
    }

    if (name.length > 100) {
      setError("Employee name cannot exceed 100 characters.");
      return;
    }

    // Validate department
    if (!department) {
      setError("Department is required.");
      return;
    }

    if (department.length > 80) {
      setError("Department cannot exceed 80 characters.");
      return;
    }

    // Validate salary
    if (
      !form.salary.trim() ||
      !Number.isFinite(salary) ||
      salary <= 0
    ) {
      setError("Salary must be greater than zero.");
      return;
    }

    if (!/^\d{1,10}(\.\d{1,2})?$/.test(form.salary)) {
      setError(
        "Salary must have at most 10 digits before and 2 digits after the decimal."
      );
      return;
    }

    // Send validated data to parent component
    onSave({
      name,
      department,
      salary: form.salary,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="card form">

      <h2>
        {employee ? "Edit Employee" : "Add Employee"}
      </h2>

      {/* Error message */}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {/* Employee Name */}
      <label htmlFor="employee-name">
        Employee Name
      </label>

      <input
        id="employee-name"
        type="text"
        name="name"
        placeholder="Enter employee name"
        maxLength={100}
        required
        value={form.name}
        onChange={handleChange}
      />

      {/* Department */}
      <label htmlFor="employee-department">
        Department
      </label>

      <input
        id="employee-department"
        type="text"
        name="department"
        placeholder="Enter department"
        maxLength={80}
        required
        value={form.department}
        onChange={handleChange}
      />

      {/* Salary */}
      <label htmlFor="employee-salary">
        Salary
      </label>

      <input
        id="employee-salary"
        type="number"
        name="salary"
        placeholder="Enter salary"
        min="0.01"
        step="0.01"
        required
        value={form.salary}
        onChange={handleChange}
      />

      {/* Action buttons */}
      <div className="actions">

        <button type="submit" disabled={saving}>
          {saving
            ? "Saving..."
            : employee
            ? "Update Employee"
            : "Add Employee"}
        </button>

        <button
          type="button"
          className="secondary"
          onClick={onCancel}
          disabled={saving}
        >
          Cancel
        </button>

      </div>
    </form>
  );
}