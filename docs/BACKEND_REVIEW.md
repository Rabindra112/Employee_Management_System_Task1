# Backend review

## Follow-up: Admin and Employee dashboards

The implementation following this audit adds separate Admin/Employee dashboards, database-enforced account-to-employee links, authenticated document routes and ownership checks, an environment-supplied JWT secret, versioned Flyway migrations, file rollback cleanup and explicit client/conflict errors. The obsolete authentication tests were repaired. Employee statistics still use an in-memory aggregate, and optimistic employee locking and deeper upload content inspection remain future improvements.

Verification after the changes: **62 backend tests passed**, frontend production build passed, and **49 local MySQL HTTP checks passed**, including link/unlink, role enforcement, upload/version/download and denial of other employees' access. Fresh database migrations and Hibernate validation also passed in an isolated schema. Temporary verification data, files, server and schema were removed. See [Admin and Employee setup](ADMIN_EMPLOYEE_SETUP.md) for current setup and permissions.

The findings below record the earlier audit before those fixes; its run results are historical.

Reviewed on 20 October 2026 against the current working tree, including the document and RSA changes.
No application source was changed for this review. Priorities reflect security exposure, correctness, then scalability.

**Evidence:** "Observed" means confirmed from source or a completed command. "Inference" describes a consequence of the code that still needs a runtime reproduction. Database and HTTP results below come from this local verification session.

## Run results

| Check | Result |
| --- | --- |
| Backend main-source compilation | Passed in the local Maven run. |
| Backend automated tests | Blocked during test compilation: `AuthControllerTest:58` calls an outdated constructor. Tests have not passed. |
| Frontend production build | Passed. |
| Frontend development server | Running at http://localhost:5174; HTTP 200 verified. Browser interaction was not automated. |
| Backend startup | Running at http://localhost:8080 against local MySQL 8.0.43. Tests skipped, schema validation enabled, login email disabled, bound to 127.0.0.1. |
| Authentication and employee HTTP checks | Passed encrypted signup/login with WebCrypto RSA-OAEP/SHA-256, duplicate signup, wrong password, profile, CORS preflight, CRUD, search/pagination, stats and validation. |
| Anonymous document access | Confirmed 200 for type and employee-document listing, and 201 for type creation without a bearer token. |
| Document upload against existing MySQL schema | Failed with 500: MySQL reports `Field 'created_at' doesn't have a default value` when inserting a document version. |
| File rollback | Confirmed one test PDF remained on disk after the failed upload rolled back its database changes. |
| Employee deletion with linked document | Confirmed 500 from the document-to-employee foreign key; deletion succeeded after removing the temporary link. |
| Expected client errors | Missing multipart file and unsupported PATCH method both returned 500; expected 400 and 405 respectively. |

Skipping test compilation for a local startup does not establish that the automated suite passes.
Temporary test accounts, employee/document records, document type and the orphaned test PDF were removed after verification. Existing records and schema were preserved. Detailed local HTTP results are in `.cache/review-smoke-results.json` (ignored by Git).

## Fix first

### 1. Protect document endpoints — critical

**Observed:** [SecurityConfig.java:146](../backend/src/main/java/com/example/ems/config/SecurityConfig.java#L146) permits every `/api/documents/**` request. Document listing, version history and downloads have no further access checks in [DocumentController.java:138](../backend/src/main/java/com/example/ems/controller/DocumentController.java#L138).

**Impact:** Anonymous type listing, employee-document listing and type creation were reproduced. Source inspection also shows version histories and file downloads have the same exposure when records exist. Uploads dereference `authentication.getName()` at [line 103](../backend/src/main/java/com/example/ems/controller/DocumentController.java#L103), so an anonymous upload can become a 500 instead of a 401.

**Fix:** Require authentication, define which users may read or change each employee's documents, and enforce that policy in the service layer. Add anonymous, forbidden-user and authorized-user tests for every document route.

### 2. Externalize and rotate the JWT signing secret — critical

**Observed:** [application.properties:13](../backend/src/main/resources/application.properties#L13) contains a literal signing secret; [SecurityConfig.java:59](../backend/src/main/java/com/example/ems/config/SecurityConfig.java#L59) uses it for token signing and verification. The value is intentionally omitted here.

**Impact:** Anyone with the configuration can sign tokens accepted by an instance using that secret.

**Fix:** Rotate it, require `app.jwt.secret=${JWT_SECRET}` without a production default, and provide the value through environment or secret management. Keep test secrets confined to test configuration.

### 3. Repair the test suite for the new authentication contract — high

**Observed:** Maven test compilation fails at [AuthControllerTest.java:58](../backend/src/test/java/com/example/ems/controller/AuthControllerTest.java#L58). The controller now requires `RsaService` as its sixth dependency at [AuthController.java:50](../backend/src/main/java/com/example/ems/controller/AuthController.java#L50), but the test supplies five arguments.

**Fix:** Inject/mock the RSA dependency, update login/signup fixtures to the encrypted-password DTOs, and preserve invalid-password and mail-failure assertions. Update the integration application context for the new document repositories and RSA key configuration. Run the complete Maven suite before treating its results as evidence.

### 4. Reconcile the existing document schema with entity mappings — high

**Observed in local database inspection:** `document_versions` has required columns `created_at`, `created_by`, `size` and `storage_key` without defaults. [DocumentVersion.java:39](../backend/src/main/java/com/example/ems/entity/DocumentVersion.java#L39) maps different fields, including `file_path`, `file_size`, `uploaded_at` and `uploaded_by`, and does not populate those four existing columns.

**Observed at runtime:** An authenticated PDF upload returned 500 because `created_at` has no default. Startup with Hibernate schema validation succeeded despite these extra, unmapped required columns.

**Fix:** Back up and inspect existing data, choose the canonical schema, then use a migration to rename/backfill columns and align constraints. Do not drop populated tables to work around the mismatch.

### 5. Make document storage consistent with database transactions — high

**Observed:** [DocumentService.java:157](../backend/src/main/java/com/example/ems/service/DocumentService.java#L157) copies the file before saving its version row; [line 196](../backend/src/main/java/com/example/ems/service/DocumentService.java#L196) does the same for new versions. Filesystem writes are outside the database transaction.

**Observed at runtime:** The failed version insert rolled back its database changes but left one test PDF on disk, which was removed during review cleanup. A failed copy leaving a partial file remains a code-level risk.

**Fix:** Introduce staged files and explicit rollback cleanup, including transaction-completion failures, plus a recovery process for abandoned files. Test database failure after file creation and a filesystem failure during copying.

### 6. Define employee deletion when documents exist — high

**Observed:** [EmployeeService.java:67](../backend/src/main/java/com/example/ems/service/EmployeeService.java#L67) deletes only the employee. [Document.java:21](../backend/src/main/java/com/example/ems/entity/Document.java#L21) has a required employee foreign key without cascading deletion.

**Observed at runtime:** A temporary document metadata record caused employee deletion to fail its foreign-key constraint and return 500. Removing that test link allowed deletion to succeed.

**Fix:** Choose an explicit retention policy: reject deletion with a clear 409, archive the employee, or deliberately remove dependent metadata and files with recovery. Cover that policy in a real database test.

## Improve next

### 7. Define permissions for employee writes

**Observed:** [SecurityConfig.java:150](../backend/src/main/java/com/example/ems/config/SecurityConfig.java#L150) requires authentication but no role for employee creation, updates or deletion. Tokens include a role, while JWT role-to-authority conversion and endpoint role rules are absent.

**Impact:** A freshly registered `USER` account successfully created, updated and deleted the temporary employee. Any registered account can change employee records and salaries. If this is intended, document it; if viewer/admin roles are intended, the policy is not enforced.

**Fix:** Define the permission matrix, configure JWT authorities consistently, apply route/service authorization and test the permitted and forbidden role combinations.

### 8. Aggregate statistics in MySQL

**Observed:** [EmployeeService.java:114](../backend/src/main/java/com/example/ems/service/EmployeeService.java#L114) fetches every employee to calculate three totals. The frontend requests these totals with each employee search/page load.

**Impact:** Each statistics request transfers and materializes the whole employee table.

**Fix:** Use an aggregate projection with `COUNT`, `SUM` and normalized `COUNT(DISTINCT ...)`; handle the empty-table sum. Measure search queries before choosing indexes or changing substring-search behavior.

### 9. Version the schema and handle concurrent edits

**Observed:** [application.properties:6](../backend/src/main/resources/application.properties#L6) uses `ddl-auto=update`; [database.sql](../database.sql) only creates the database. [Employee.java:17](../backend/src/main/java/com/example/ems/entity/Employee.java#L17) has no optimistic-lock version.

**Fix:** Adopt Flyway/Liquibase migrations and `ddl-auto=validate` outside disposable development databases. Add `@Version` and a client-visible version/precondition to prevent stale employee edits overwriting newer changes; return a clear conflict response.

### 10. Map expected client errors explicitly

**Observed:** [GlobalExceptionHandler.java:114](../backend/src/main/java/com/example/ems/exception/GlobalExceptionHandler.java#L114) catches remaining exceptions as 500. Missing multipart file and unsupported PATCH requests both returned 500 during verification. Duplicate document-type races and upload-size rejection also lack dedicated mappings. File validation at [DocumentService.java:78](../backend/src/main/java/com/example/ems/service/DocumentService.java#L78) trusts the client-supplied content type.

**Fix:** Map known unique/foreign-key conflicts to 409, oversized bodies to 413, missing parameters to 400 and unsupported methods to 405. Use structured field errors and content inspection for uploads. Keep unexpected server details out of responses and avoid logging sensitive request data.

### 11. Add persistence and document coverage

**Observed:** [AuthFlowIntegrationTest.java:53](../backend/src/test/java/com/example/ems/controller/AuthFlowIntegrationTest.java#L53) disables datasource/JPA auto-configuration and mocks repositories. It covers HTTP/security behavior, but cannot verify MySQL constraints, schema compatibility or transaction rollback. There are no document-specific test classes in the current tree.

**Fix:** Add MySQL integration tests for migrations, upload/version/download, version uniqueness under concurrency, authorization, employee deletion with documents and file cleanup after rollback. Add empty/mixed-department statistics cases and keep existing mail executor/failure tests. If guaranteed login-alert delivery is required, replace best-effort in-memory sending with persisted jobs, retries and delivery metrics.

## Configuration cleanup

Correct `cspring.application.name` to `spring.application.name` at [application.properties:1](../backend/src/main/resources/application.properties#L1). Make the workstation-specific RSA key paths at [line 32](../backend/src/main/resources/application.properties#L32) environment-configurable, and give the application a dedicated database account instead of defaulting to MySQL root. Separate local development settings from production configuration.
