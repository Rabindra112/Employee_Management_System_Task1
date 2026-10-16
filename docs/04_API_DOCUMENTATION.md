# REST API Documentation

**Base URL:** `http://localhost:8080/api` (configured `server.port=8080`). **Media type:** `application/json`. Protected calls require `Authorization: Bearer <JWT>`.

## Authentication
| Method | Path | Access | Success |
|---|---|---|---|
| POST | `/auth/signup` | Public | 201 + message |
| POST | `/auth/login` | Public | 200 + accessToken, tokenType, expiresIn, user |
| GET | `/auth/me` | Authenticated | 200 + user profile |

**Signup JSON**
```json
{"fullName":"Rabindra kr","email":"rabindra@example.com","password":"Example@123","confirmPassword":"Example@123"}
```
Signup response (201): `{"message":"Account created. Please log in."}`. Duplicate email: 409. Password mismatch: 400. Signup requires nonblank name (max 100), valid email, password length 8–72, nonblank confirmation.

**Login JSON**
```json
{"email":"sample@example.com","password":"Example@123"}
```
Successful login response shape:
```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":3600,"user":{"id":1,"fullName":"Sample User","email":"sample@example.com","role":"USER"}}
```
Invalid credentials: 401. `/auth/me` returns the same `user` profile shape.

## Employees — all require authentication
| Method | Path | Purpose | Success |
|---|---|---|---|
| POST | `/employees` | Create | 201 + employee |
| GET | `/employees` | Search, filter, paginate | 200 + Spring Page |
| GET | `/employees/stats` | Dashboard statistics | 200 + stats object |
| GET | `/employees/{id}` | Retrieve one | 200 + employee |
| PUT | `/employees/{id}` | Replace employee fields | 200 + employee |
| DELETE | `/employees/{id}` | Delete | 204 no body |

**Create/update body — exact `EmployeeRequest` fields:**
```json
{"name":"Rabindra Kumar","department":"IT","salary":45000.00}
```
The uploaded version does **not** accept/persist `address`.

**Example employee response:**
```json
{"id":1,"name":"Rabindra Kumar","department":"IT","salary":45000.00}
```

**List query parameters:** `name` default `""`, `department` default `""`, `page` default `0` (min 0), `size` default `10` (range 1–100). Example: `/employees?name=rahul&department=IT&page=0&size=10`. Response is Spring Data `Page` containing `content`, `totalElements`, `totalPages`, and pagination metadata.

**Stats response keys:** `totalEmployees`, `totalDepartments`, `totalSalary`.

**Validation:** name required/max 100, department required/max 80, salary required/>0/at most 10 integral and 2 decimal digits, path ID positive. Missing ID → 404; invalid request → 400. Central exception handler generally responds with `timestamp`, `status`, `message`, `path`; security-generated 401/403 responses may have a different shape.

**Postman order:** signup → login → copy `accessToken` → set Authorization/Bearer Token → create → list → update → delete.
