# Test Cases and Verification Plan

**Evidence status:** These are test scenarios derived from controller/DTO/security code. Test source files exist (`AuthControllerTest`, `AuthFlowIntegrationTest`, `EmployeeServiceTest`, `EmailServiceTest`, `GlobalExceptionHandlerTest`), but **tests were not executed as part of this documentation review**. Do not mark them passed without running them.

| ID | Scenario | Request / setup | Expected |
|---|---|---|---|
| AUTH-01 | Valid signup | POST `/api/auth/signup`, valid body | 201; success message |
| AUTH-02 | Duplicate email | Signup twice with same email | 409 |
| AUTH-03 | Password mismatch | Different password and confirmation | 400 |
| AUTH-04 | Invalid signup email | Email without valid format | 400 |
| AUTH-05 | Short password | Fewer than 8 chars | 400 |
| AUTH-06 | Valid login | Existing user + correct password | 200; JWT + user |
| AUTH-07 | Invalid login | Incorrect password | 401 |
| AUTH-08 | Profile without token | GET `/api/auth/me` | 401 |
| AUTH-09 | Profile with JWT | GET `/api/auth/me` | 200; profile |
| EMP-01 | Create employee | POST `/api/employees`, valid JSON + JWT | 201; ID present |
| EMP-02 | Missing name | POST blank `name` | 400 |
| EMP-03 | Salary zero | POST `salary: 0` | 400 |
| EMP-04 | Create without JWT | POST without Authorization | 401 |
| EMP-05 | List employees | GET `/api/employees` + JWT | 200; page payload |
| EMP-06 | Search | GET `?name=rahul` | 200; filtered page |
| EMP-07 | Department filter | GET `?department=IT` | 200; filtered page |
| EMP-08 | Invalid page size | GET `?size=101` | 400 |
| EMP-09 | Get by ID | GET `/api/employees/{existingId}` | 200 |
| EMP-10 | Missing ID | GET `/api/employees/99999999` | 404 |
| EMP-11 | Update | PUT existing ID + valid JSON | 200; changed values |
| EMP-12 | Delete | DELETE existing ID | 204 |
| EMP-13 | Stats | GET `/api/employees/stats` | 200; 3 stat keys |
| SEC-01 | USER role create | Logged-in USER POST `/api/employees` | **Currently permitted by configured rules**; document RBAC gap |
| MAIL-01 | Login alert | Login with SMTP configured | Check mail delivery separately; do not infer from 200 |

## Execution log template
| Test ID | Actual status | Actual result | Pass/Fail | Screenshot / evidence |
|---|---|---|---|---|
| AUTH-01 | Not run | Not run | Not verified | — |
| EMP-01 | Not run | Not run | Not verified | — |

## Run automated tests
From `backend/`: `mvn test`. Capture the console summary and test reports from `backend/target/surefire-reports/` if generated. For Postman, capture request, status and response; redact JWTs, credentials, and personal data.
