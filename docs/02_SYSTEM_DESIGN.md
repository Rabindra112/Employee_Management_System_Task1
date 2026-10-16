# System Design

## Architecture
```text
Browser (React / Vite)
  | Axios requests; JWT from sessionStorage
  v
Spring Security filter chain (stateless JWT resource server)
  | permit POST /api/auth/login and /api/auth/signup
  v
REST controllers: AuthController, EmployeeController
  |                     |
  |                     v
  |                 EmployeeService
  |                     |
  v                     v
UserRepository       EmployeeRepository
  |                     |
  +---------- MySQL ----+

AuthController -> EmailService -> async executor -> SMTP (login alert)
```

## Request flows
**Signup:** React form → `POST /api/auth/signup` → validation and matching passwords → lowercase/trim email → unique-email check → BCrypt encode → `app_users` insert → 201.

**Login:** React form → `POST /api/auth/login` → AuthenticationManager/DaoAuthenticationProvider → JWT HS256 signed with configured secret → response with token and profile → schedule asynchronous login alert → frontend stores token and opens dashboard.

**Employee create:** Axios adds `Authorization: Bearer ...` → JWT authentication → `EmployeeController.create` → `EmployeeService.create` → JPA save → HTTP 201 and employee JSON.

**Employee list:** controller accepts `name`, `department`, `page`, `size`; service creates page request sorted `id DESC`; repository executes derived case-insensitive contains query.

## Separation of concerns
- `controller/`: HTTP endpoints, input validation, status codes.
- `dto/`: request shapes and Bean Validation.
- `service/`: employee business operations and email delivery.
- `repository/`: persistence abstractions.
- `entity/`: JPA database mappings.
- `config/`: security and asynchronous mail execution.
- `exception/`: standardized API error responses.

