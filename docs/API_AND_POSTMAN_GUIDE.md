# Employee Management System API and Postman Guide

This is the API reference for the current project, organized by topic with Postman steps and examples. The API uses JSON for normal requests, multipart form data for file uploads, and bearer JWT authentication for protected routes.

## 1. Postman setup

Start the backend and database. The default API base URL is:

```text
http://localhost:8080/api
```

Create a Postman environment with:

| Variable | Initial value |
| --- | --- |
| `baseUrl` | `http://localhost:8080/api` |
| `token` | *(empty)* |
| `employeeId` | *(empty)* |
| `accountId` | *(empty)* |
| `documentTypeId` | *(empty)* |
| `documentId` | *(empty)* |
| `versionNumber` | `1` |
| `executionId` | *(empty)* |
| `uploadId` | *(empty)* |

For protected requests, choose **Authorization → Bearer Token** and set the token to `{{token}}`. Alternatively, add `Authorization: Bearer {{token}}` as a header. Do not set a global JSON content type for multipart requests; let Postman create the multipart boundary.

### Password encryption for signup and login

Signup and login expect RSA OAEP-encrypted passwords, not plain text. First call `GET {{baseUrl}}/auth/public-key`. The JSON response contains `publicKey` as a Base64-encoded DER RSA public key (not PEM). Decode it as X.509 SubjectPublicKeyInfo, encrypt the UTF-8 password with RSA OAEP using SHA-256 and MGF1 SHA-256, then Base64-encode the ciphertext. Use the resulting Base64 strings as `encryptedPassword` and `encryptedConfirmPassword`.

Postman does not have a built-in RSA OAEP request helper. For a manual Postman workflow, use a local pre-request script with a vetted RSA/OAEP crypto library available in the Postman sandbox, or generate the Base64 ciphertext outside Postman using a compatible tool. Ensure the OAEP digest and MGF1 digest are both SHA-256. Never send plaintext password fields to these endpoints; the DTO does not accept them.

Sample signup request shape (replace placeholders with Base64 ciphertexts):

```http
POST {{baseUrl}}/auth/signup
Content-Type: application/json
```

```json
{
  "fullName": "Example Employee",
  "email": "employee@example.com",
  "encryptedPassword": "<base64-rsa-oaep-sha256-ciphertext>",
  "encryptedConfirmPassword": "<base64-rsa-oaep-sha256-ciphertext>"
}
```

Signup returns `201 Created`. Public signup always creates an Employee account. To get a token, call `POST {{baseUrl}}/auth/login` with `email` and `encryptedPassword`, then copy `accessToken` from the response into the `token` environment variable. The response includes token expiry and a user profile. `GET /auth/me` returns the current profile.

To create an Admin for local development, first register normally, then use the database administration procedure described in [Admin and Employee setup](ADMIN_EMPLOYEE_SETUP.md#set-up-the-first-admin). Do not expose an Admin role via public signup.

## 2. Authentication endpoints

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/auth/public-key` | Public | Get RSA public key for password encryption |
| POST | `/auth/signup` | Public | Register Employee account |
| POST | `/auth/login` | Public | Authenticate and get JWT |
| GET | `/auth/me` | Any authenticated account | Get current account profile |

Expected errors include `400` for invalid encrypted data, invalid input, password mismatch, or invalid password length; `401` for bad credentials; and `409` for duplicate email. Signup validates full name (max 100), email (max 190), and decrypted password (8–72 UTF-8 bytes).

## 3. Employees

Employee record management is Admin-only. The current employee's linked record can be read by an Admin or Employee.

| Method | Path | Parameters | Purpose |
| --- | --- | --- | --- |
| POST | `/employees` | JSON body | Create employee |
| GET | `/employees` | `name`, `department`, `page`, `size` | Search and paginate |
| GET | `/employees/stats` | — | Dashboard totals |
| GET | `/employees/me` | — | Get linked employee record for current account |
| GET | `/employees/{id}` | Path ID | Get employee |
| PUT | `/employees/{id}` | Path ID, JSON body | Replace employee fields |
| DELETE | `/employees/{id}` | Path ID | Delete employee (204 response) |

Create/update JSON:

```json
{
  "name": "Asha Rao",
  "department": "Engineering",
  "salary": 72000.00
}
```

Name is required (max 100), department is required (max 80), and salary must be positive with at most 10 integer digits and 2 decimal places. List defaults: `page=0`, `size=10`; size must be 1–100. Example:

```http
GET {{baseUrl}}/employees?name=Asha&department=Engineering&page=0&size=10
```

The list response is a Spring Page with `content`, `totalElements`, `totalPages`, and page metadata. Stats returns `totalEmployees`, `totalDepartments`, and `totalSalary`.

**Postman sequence:** with an Admin token, create an employee, save its returned `id` as `employeeId`, then list/get/update/delete as needed. Employee deletion may return `409` when account/document relationships prevent it; resolve those relationships before retrying.

## 4. Admin accounts and roles

All these routes require Admin access.

| Method | Path | Body | Purpose |
| --- | --- | --- | --- |
| GET | `/admin/accounts` | — | List accounts and employee links |
| PUT | `/admin/accounts/{accountId}/employee` | `{"employeeId": 123}` | Link account to employee record |
| GET | `/admin/users` | — | List user accounts |
| PATCH | `/admin/users/{userId}/role` | `{"role":"ADMIN"}` or `{"role":"EMPLOYEE"}` | Update account role |

The account-link request takes a nullable employee ID. One employee record can be linked to one account; only Employee accounts can be linked. To link an Employee, first list accounts and employees, then set `accountId` and `employeeId` from their responses and call the PUT route. To unlink, send `{"employeeId":null}`.

Role update accepts `USER` or `ADMIN`. Public signup creates `EMPLOYEE`; role listing displays `EMPLOYEE` accounts as `USER` for compatibility, and changing an account to `USER` can store that role. Check the response's role before using it. Role changes are read from the database for each authenticated request, so changed permissions apply to existing tokens.

## 5. Documents and version review

Document access is role-dependent. Employees can read and upload their own account documents, and download only approved versions. Admins manage document types and employee documents and review pending versions. A file must be PDF, DOCX, JPG/JPEG, or PNG; maximum size is 10 MB. The app validates extension, MIME type, and file signature.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/documents/types` | Authenticated | List document types |
| POST | `/documents/types` | Admin | Create document type |
| GET | `/documents/my` | Employee | List own uploaded documents |
| POST | `/documents/my/upload` | Employee | Upload new own document (multipart) |
| POST | `/documents/{documentId}/resubmit` | Employee owner | Submit next version after latest version was rejected (multipart) |
| GET | `/documents/pending` | Admin | List pending versions |
| PATCH | `/documents/{documentId}/versions/{versionNumber}/approve` | Admin | Approve pending version |
| PATCH | `/documents/{documentId}/versions/{versionNumber}/reject` | Admin | Reject pending version |
| GET | `/documents/{documentId}/versions` | Admin or authorized Employee | List version history |
| GET | `/documents/{documentId}/versions/{versionNumber}/download` | Admin or authorized Employee | Download version |
| POST | `/documents/upload` | Admin | Upload document for an employee (multipart) |
| POST | `/documents/{documentId}/versions` | Admin | Add admin-managed version (multipart) |
| GET | `/documents/employee/{employeeId}` | Admin or linked Employee | List employee documents |

### Document types

Create a type with:

```http
POST {{baseUrl}}/documents/types
Content-Type: application/json
```

```json
{"name":"PASSPORT","description":"Passport identification page"}
```

Name is required and limited to 100 characters; description is limited to 255. Duplicate names return `409`. Use the returned `id` as `documentTypeId`.

### Employee upload (multipart)

In Postman, select **Body → form-data** and add:

| Key | Type | Value |
| --- | --- | --- |
| `documentTypeId` | Text | `{{documentTypeId}}` |
| `file` | File | Select a supported document |

Send `POST {{baseUrl}}/documents/my/upload`. The Employee account must be linked to an employee record. The response includes document ID, current version, status, and owner. Save the returned `id` as `documentId` and set `versionNumber` to `1` for review.

### Admin review and employee resubmission

1. As Admin, call `GET {{baseUrl}}/documents/pending` and copy a pending row's `documentId` and `versionNumber`.
2. Approve with `PATCH {{baseUrl}}/documents/{{documentId}}/versions/{{versionNumber}}/approve` (no body), or reject with the same path ending `/reject` and JSON:

   ```json
   {"reason":"The scan is not readable. Please upload a clearer copy."}
   ```

3. Rejection reason is required and limited to 1000 characters. Only pending versions can be reviewed; the uploader cannot review their own upload.
4. As the owning Employee, call `GET /documents/my` and confirm the latest status. A rejected current version can be resubmitted through **Body → form-data** with key `file` (type File) to `POST {{baseUrl}}/documents/{{documentId}}/resubmit`. The new version is pending review.
5. Check history with `GET {{baseUrl}}/documents/{{documentId}}/versions`.

For a download request, select **Send and Download** in Postman, or save the response body as a file. An Employee receives `403` for an unapproved version; Admin can download versions for review.

### Admin upload routes

For `POST /documents/upload`, use multipart fields `employeeId`, `documentTypeId` (Text), and `file` (File). For `POST /documents/{documentId}/versions`, use multipart field `file`. These routes create admin-managed employee documents/versions; ownership and version approval behavior can differ from Employee-owned documents.

## 6. Employee CSV batch import and exports

These routes require Admin access; import endpoints are available when `app.employee-import.enabled` is enabled (default true). Export routes are available when `app.employee-export.enabled` is enabled (default true).

| Method | Path | Body / response | Purpose |
| --- | --- | --- | --- |
| GET | `/admin/employees/template` | CSV download | Download header template |
| GET | `/admin/employees/export.csv` | CSV download | Export employees |
| GET | `/admin/employees/export.xlsx` | XLSX download | Export employees |
| POST | `/admin/employee-import/uploads` | multipart `file` | Store CSV upload, returns `uploadId` |
| POST | `/admin/employee-import/jobs` | JSON `{"uploadId":"..."}` | Start batch job |
| GET | `/admin/employee-import/jobs/{executionId}` | JSON | Get job status and counts |
| GET | `/admin/employee-import/jobs` | JSON array | Recent import history (up to 100 job instances) |
| GET | `/admin/employee-import/jobs/{executionId}/errors` | CSV download | Download row errors |

The CSV header must be `name,department,salary` in that order; header matching ignores case and surrounding whitespace. Each row needs exactly three fields. Valid rows are inserted in chunks of 100. Invalid rows are skipped and recorded in the error CSV. Exact duplicate name/department/salary rows within one upload are rejected; existing database rows are not checked for duplicates.

**Postman sequence:**

1. `GET {{baseUrl}}/admin/employees/template`; save the CSV and fill it with rows.
2. `POST {{baseUrl}}/admin/employee-import/uploads`; choose **Body → form-data**, key `file`, type File. Copy `uploadId` from the response into the environment.
3. `POST {{baseUrl}}/admin/employee-import/jobs` with JSON `{"uploadId":"{{uploadId}}"}`. Copy `executionId` from the response.
4. Poll `GET {{baseUrl}}/admin/employee-import/jobs/{{executionId}}` until status is `COMPLETED` or `FAILED`.
5. If `errorCount` is greater than zero, use **Send and Download** on `GET {{baseUrl}}/admin/employee-import/jobs/{{executionId}}/errors`.

Each upload may be launched once. A job that fails after earlier chunks may have inserted some employees; inspect counts and database state before re-uploading corrected data.

## 7. Authorization summary

| Role | Typical access |
| --- | --- |
| Public | Public key, signup, login |
| Employee | Own profile, own document list/upload/history, approved own document downloads, linked employee profile/documents |
| Admin | Employee CRUD/statistics, account linking and role management, document types/review/admin document operations, employee import/export |

All other API routes require a valid JWT. The JWT issuer is `employee-management`; the token is signed using the configured `JWT_SECRET`. The decoder requires at least 32 bytes for this secret. Use the local startup script or set the secret through the supported environment configuration.

## 8. Common HTTP responses

| Status | Meaning |
| --- | --- |
| 200 | Successful read/update/action |
| 201 | Created account, employee, or uploaded document |
| 204 | Successful employee deletion |
| 400 | Invalid input, malformed data, or missing required field |
| 401 | Missing, expired, invalid token, or bad credentials |
| 403 | Authenticated account lacks required role/ownership |
| 404 | Requested account, employee, document, version, or job not found |
| 409 | Duplicate/conflicting resource or invalid state transition |
| 413 | Document upload exceeds 10 MB |

Error JSON may be produced by the global exception handler or Spring Security. Check the status code and response body; do not assume all errors share one JSON shape.

## 9. Suggested Postman collection order

1. Authentication: fetch public key, signup an Employee, login, and set `token`.
2. Admin bootstrap: promote a designated account to Admin using the documented local database procedure; login again.
3. Employee setup: create employee, list accounts, link Employee account to employee record.
4. Batch: download template, upload CSV, start job, poll status, download errors if any.
5. Documents: create type as Admin, upload as Employee, review as a different Admin, check history and approved download as Employee.

## Source locations

- Controllers: `backend/src/main/java/com/example/ems/controller/` and `backend/src/main/java/com/example/ems/batch/`
- DTOs and validation: `backend/src/main/java/com/example/ems/dto/`
- Route authorization: `backend/src/main/java/com/example/ems/config/SecurityConfig.java`
- Storage and document rules: `backend/src/main/java/com/example/ems/service/DocumentService.java`
- Existing workflow guide: [Batch Processing and Document Versioning](BATCH_PROCESSING_AND_DOCUMENT_VERSIONING.md)
