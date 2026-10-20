# Admin and Employee dashboards

There is one login form. After authentication, the account's stored role selects the dashboard:

| Role | Dashboard | Access |
| --- | --- | --- |
| ADMIN | `/admin/dashboard` | Manage employees, view company statistics, link/unlink employee accounts, manage document API operations. |
| EMPLOYEE | `/employee/dashboard` | View own linked employee details, salary, document history and downloads. |

Public signup always creates an EMPLOYEE account. Existing USER accounts migrate to EMPLOYEE. Signup cannot choose an Admin role or attach itself to an employee record. Employee records are linked by an Admin, rather than guessed from names or email addresses.

## Run locally on Windows

Requirements: Java 21, Maven, MySQL and Node.js. Start MySQL and create the database if needed:

```sql
CREATE DATABASE IF NOT EXISTS employee_management;
```

From the project root, start the backend:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\run-local.ps1
```

This script generates a development JWT secret in memory, binds the API to localhost and disables login-alert emails unless you explicitly enable them. Sign in again after restarting if you use its generated secret. To use your own database credentials or a stable secret, set `DB_USERNAME`, `DB_PASSWORD`, `DB_URL` and `JWT_SECRET` in your terminal before starting. Direct `mvn spring-boot:run` requires `JWT_SECRET` with at least 32 bytes.

The default RSA paths are `../secrets/private_key.pem` and `../secrets/public_key.pem`, relative to the backend directory. Override them with `RSA_PRIVATE_KEY_PATH` and `RSA_PUBLIC_KEY_PATH`. Keep private keys outside version control. Existing local keys can be reused.

In a second terminal:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open http://localhost:5174. The API runs on http://localhost:8080.

## Set up the first Admin

1. Use Sign Up to register the account you intend to administer the system.
2. Open MySQL Workbench or the MySQL command line as a database administrator, and select `employee_management`.
3. Replace the email below with the exact registered email and run:

```sql
UPDATE app_users
SET role = 'ADMIN', employee_id = NULL
WHERE email = 'your-registered-email@example.com';

SELECT id, email, role FROM app_users
WHERE email = 'your-registered-email@example.com';
```

Confirm the query finds the intended account. This changes only that account. There is no shared Admin password or public Admin-registration route.

4. Log out and log in again. The Admin Dashboard opens automatically. Database role changes also affect API permissions on existing tokens immediately; logging in again refreshes the dashboard selection.

## Set up an Employee

1. The employee registers their own account using Sign Up.
2. In the Admin Dashboard, add the employee's employment record with name, department and salary, or use an existing record.
3. In Employee account access, choose the employee's registered account and enter the matching employee record ID. Save the link.
4. The employee logs in or refreshes their dashboard to see their own details and documents.

Until linked, the Employee Dashboard shows an assignment-pending message. One employee record can be linked to one account. Unlinking removes access to that employee's information. Resolve attached documents and unlink the account before deleting an employee; otherwise the API returns 409 with an explanation.

## Schema and verification

Flyway versions the schema and Hibernate validates it at startup. V1 creates the base tables for a fresh database. Existing databases are baselined at version 0; V2 adds the account-to-employee link, maps USER roles to EMPLOYEE, and preserves legacy document columns while allowing the current document-version inserts. No existing tables or records are dropped.

Back up an existing database before applying schema changes. A migration error stops startup and must be resolved before using that database. These migrations target MySQL 8.

```powershell
cd backend
mvn.cmd -B -ntp '-Dmaven.repo.local=../.cache/maven-repository' test
cd ../frontend
npm.cmd run build
```

Security tests cover Admin/Employee separation, document ownership, unlinked accounts, stale/deleted account tokens and public signup restrictions. Storage tests cover rollback and interrupted-copy cleanup.

Verified on 20 October 2026: 62 backend tests, the frontend production build and 49 HTTP checks against local MySQL all passed. An isolated fresh database also passed Flyway V1/V2 migrations and Hibernate validation; its temporary server and schema were removed afterward. Browser interaction was not automated. Verification accounts and uploaded files were cleaned up, and the local application remains running on ports 5174 and 8080.
