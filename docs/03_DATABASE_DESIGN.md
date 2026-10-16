# Database Design

## Database
`employee_management` (created by `database.sql`). JPA configuration uses `spring.jpa.hibernate.ddl-auto=update`, so Hibernate updates tables at application startup; the SQL file does **not** contain a full table DDL export.

## `employees` table — `Employee.java`
| Column | Java type | Mapping / constraint |
|---|---|---|
| `id` | `Long` | Primary key, IDENTITY-generated |
| `name` | `String` | NOT NULL, length 100 |
| `department` | `String` | NOT NULL, length 80 |
| `salary` | `BigDecimal` | NOT NULL, DECIMAL(12,2) |

**Important:** No `address` column exists in the uploaded entity. Do not insert `address` into the current API request until implemented.

## `app_users` table — `User.java`
| Field | Java type | Mapping / constraint |
|---|---|---|
| `id` | `Long` | Primary key, IDENTITY-generated |
| `fullName` | `String` | NOT NULL, length 100; physical column naming follows Hibernate strategy |
| `email` | `String` | NOT NULL, unique, length 190 |
| `password` | `String` | NOT NULL, BCrypt hash |
| `role` | `String` | NOT NULL, default Java field value `USER` |
| `createdAt` | `Instant` | NOT NULL, non-updatable; initialized in Java |

There is no JPA relationship between employees and users in the supplied entities.

## Repositories
- `EmployeeRepository extends JpaRepository<Employee,Long>` with `findByNameContainingIgnoreCaseAndDepartmentContainingIgnoreCase(..., Pageable)` and `countByDepartmentIgnoreCase(...)`.
- `UserRepository extends JpaRepository<User,Long>` with `findByEmail` and `existsByEmail`.

## Verification queries
```sql
CREATE DATABASE IF NOT EXISTS employee_management;
USE employee_management;
SHOW TABLES;
DESCRIBE employees;
DESCRIBE app_users;
SELECT id, name, department, salary FROM employees ORDER BY id DESC;
SELECT id, email, role FROM app_users;
```
Avoid selecting or exporting password hashes in submission screenshots.
